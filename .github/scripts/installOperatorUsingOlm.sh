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
cd $OPERATOR_LOCATION || exit
"$CURRENT_PWD"/mvnw clean package -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.insecure=true \
  -Dquarkus.container-image.registry=$KIND_REGISTRY \
  -Dquarkus.container-image.group=$REGISTRY_NAMESPACE \
  -Dquarkus.kubernetes.namespace=$K8S_NAMESPACE

# Build Operator Bundle
docker build -t $BUNDLE_IMAGE -f target/bundle/$NAME-operator/bundle.Dockerfile target/bundle/$NAME-operator
docker push $BUNDLE_IMAGE

# Build Catalog image
opm version
opm index add --bundles $BUNDLE_IMAGE --tag $CATALOG_IMAGE --build-tool docker --use-http --skip-tls-verify
docker push $CATALOG_IMAGE

# Create OLM catalog resource
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: $NAME-catalog
  namespace: $K8S_NAMESPACE
spec:
  sourceType: grpc
  image: $CATALOG_IMAGE
EOF

# Wait until the catalog source of our operator is up and running
if ! "$CURRENT_PWD"/.github/scripts/waitFor.sh pods $K8S_NAMESPACE Running "--selector=olm.catalogSource=$NAME-catalog -o jsonpath='{..status.phase}'"; then
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
  name: $NAME-operator
  source: $NAME-catalog
  sourceNamespace: $K8S_NAMESPACE
EOF

# Wait until the operator is up and running
if ! "$CURRENT_PWD"/.github/scripts/waitFor.sh csv $K8S_NAMESPACE Succeeded "$NAME-operator -o jsonpath='{.status.phase}'"; then
  exit 1;
fi

cd "$CURRENT_PWD" || exit
exit 0;
