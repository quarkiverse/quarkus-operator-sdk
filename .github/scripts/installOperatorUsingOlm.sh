#!/usr/bin/env bash
NAME="${1}"
OPERATOR_LOCATION="${2}"
KIND_REGISTRY="${3-localhost:5000}"
K8S_NAMESPACE=operators
REGISTRY_NAMESPACE=$NAME
BUNDLE_IMAGE=$KIND_REGISTRY/$REGISTRY_NAMESPACE/$NAME-manifest-bundle:latest
CATALOG_IMAGE=$KIND_REGISTRY/$REGISTRY_NAMESPACE/$NAME-manifest-catalog:latest
CURRENT_PWD=$(pwd)

# Create and set namespace
kubectl config set-context --current --namespace=$K8S_NAMESPACE

# Build manifests and images
cd "$OPERATOR_LOCATION" || exit
"$CURRENT_PWD"/mvnw clean package -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.insecure=true \
  -Dquarkus.container-image.registry="$KIND_REGISTRY" \
  -Dquarkus.container-image.group="$REGISTRY_NAMESPACE" \
  -Dquarkus.kubernetes.namespace=$K8S_NAMESPACE

copy_image_to_registry() {
  # create image and push it to the insecure internal registry, using skopeo since it seems it's not possible to do it directly using docker only
  docker save "$1" | skopeo copy --insecure-policy --dest-tls-verify=false docker-archive:/dev/stdin docker://"$1"
}

# Build Operator Bundle
bundle_name="$NAME"-operator
catalog_name="$NAME"-catalog
docker build -t "$BUNDLE_IMAGE" -f target/bundle/"$bundle_name"/bundle.Dockerfile target/bundle/"$bundle_name"
copy_image_to_registry "$BUNDLE_IMAGE"

# Build Catalog image
opm index add --bundles "$BUNDLE_IMAGE" --tag "$CATALOG_IMAGE" --build-tool docker --use-http
copy_image_to_registry "$CATALOG_IMAGE"

# Create OLM catalog resource
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: $catalog_name
  namespace: $K8S_NAMESPACE
spec:
  sourceType: grpc
  image: $CATALOG_IMAGE
EOF

# Wait until the catalog source of our operator is up and running
if ! "$CURRENT_PWD"/.github/scripts/waitFor.sh pods $K8S_NAMESPACE Running "--selector=olm.catalogSource=$catalog_name -o jsonpath='{..status.phase}'"; then
  exit 1;
fi

# Create a Subscription which triggers the deployment of joke operator
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: $NAME-subscription
  namespace: $K8S_NAMESPACE
spec:
  channel: alpha
  name: $bundle_name
  source: $catalog_name
  sourceNamespace: $K8S_NAMESPACE
EOF

# Wait until the operator is up and running
if ! "$CURRENT_PWD"/.github/scripts/waitFor.sh csv $K8S_NAMESPACE Succeeded "$bundle_name -o jsonpath='{.status.phase}'"; then
  exit 1;
fi

cd "$CURRENT_PWD" || exit
exit 0;
