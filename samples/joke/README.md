## Spamming your Kubernetes cluster with jokes since 2021! :heart:

The idea is that you create a `JokeRequest` custom resource that you apply to your cluster. The
operator will do its best to comply and create a `Joke` custom resource on your behalf if everything
went well. Jokes are retrieved from the https://v2.jokeapi.dev/joke API endpoint. The request can be
customized to your taste by specifying which category of jokes you'd like or the amount of
explicitness / topics you can tolerate. You can also request a "safe" joke which should be
appropriate in most settings.

### Quick start

To quickly test your operator (and develop it), no need to create an image and deploy it to the
cluster. You can just follow the steps below to get started quickly:

- Connect to your cluster of choice using `kubectl/oc`, select the appropriate namespace/project.
  The operator will automatically connect to that cluster/namespace combination when started.
- Run `mvn install` on the parent directory to build the project locally. This will automatically generate several resources for
  you, in particular the CRDs associated with the custom resources we will be dealing with. These
  CRDs are generated in `target/manifests/` and come in `v1` version which correspond to the versions of the CRD spec. We recommend you use
  the `v1` version but might need to fall back to `v1beta1` if you're connecting to an older
  cluster.
- Deploy the CRDs to your cluster (requires cluster admin privileges, while this shouldn't be an
  issue for most "testing" clusters such as `minikube` or `kind`, you might need to log in to your
  OpenShift clusters with an admin account):
  ```sh
  kubectl apply -f target/manifests/jokerequests.samples.javaoperatorsdk.io-v1.yml
  kubectl apply -f src/main/k8s/jokes.samples.javaoperatorsdk.io-v1.yml
  ```           
- Launch the app in dev mode: `mvn quarkus:dev`
- Deploy the test request (or your own): `kubectl apply -f src/main/k8s/jokerequest.yml`. The operator will take your request and attempt to retrieve a joke from the api. If everything went well, a `Joke` resource named after the `id` of the joke retrieved from the API will be created on your cluster.
- You can check the status of the request by doing something similar to, `jr` being the short name associated with `JokeRequest`:
    ```sh
    kubectl describe jr
    ```
- You can check if your joke has been created and look for the id in the returned list:
    ```sh
    kubectl get jokes
    ```
  - Check your joke:
    ```sh
    kubectl get jokes <your joke id> -o jsonpath="{.joke}{'\n'}" 
    ```

### Deployment

This section explains how to deploy your operator using the [Operator Lifecycle Manager (OLM)](https://olm.operatorframework.io/) by following the next steps:

0. Requirements

Make sure you have installed the [opm](https://github.com/operator-framework/operator-registry) command tool and have connected to a Kubernetes cluster with the OLM installed.

2. Generate the Operator image and bundle manifests

This example uses the [Quarkus Jib container image](https://quarkus.io/guides/container-image#jib) extension to build the Operator image. 
Also, the Quarkus Operator SDK provides the `quarkus-operator-sdk-csv-generator` extension that generates the Operator bundle manifests at `target/manifests`.
So, you simply need to run the next Maven command to build and push the operator image, and also to generate the bundle manifests:

```shell
mvn clean package -Dquarkus.container-image.build=true \
    -Dquarkus.container-image.push=true \
    -Dquarkus.container-image.registry=<your container registry. Example: quay.io> \
    -Dquarkus.container-image.group=<your container registry namespace> \
    -Dquarkus.kubernetes.namespace=<the kubernetes namespace where you will deploy the operator>
```

| If you're using an insecure container registry, you'd also need to append the next property to the Maven command `-Dquarkus.container-image.insecure=true`.

2. Build the Operator Bundle image

An Operator Bundle is a container image that stores Kubernetes manifests and metadata associated with an operator. You can find more information about this in [here](https://olm.operatorframework.io/docs/tasks/creating-operator-bundle/). 
In the previous step, we generated the manifests at `target/manifests`, but we still need to generate the `metadata/annotations.yaml` file and the final Operator Bundle image. For doing this, we will use the [opm](https://github.com/operator-framework/operator-registry) utility:

```shell
opm alpha bundle generate \
    --directory target/manifests \
    --package <The name of the package that bundle image belongs to> \ 
    --channels <The list of channels that bundle image belongs to> \
    --default <The default channel for the bundle image>
```

For example, if we want to name the package `joke` and use the `alpha` channels, the command would look like as `opm alpha bundle generate --directory target/manifests --package joke --channels alpha --default alpha`.

The above command will generate a `bundle.Dockerfile` Dockerfile file that you will use to build and push the final Operator Bundle image to your container registry:

```shell
docker build -t <your container registry>/<your container registry namespace>/joke-manifest-bundle:<tag> -f bundle.Dockerfile .
docker push <your container registry>/<your container registry namespace>/joke-manifest-bundle:<tag>
```

3. Make your operator available within a Catalog

OLM uses catalogs to discover and install Operators and their dependencies. So, a catalog is similar to a repository of operators and its versions that can be installed on a cluster.
So far, we have built the Operator bundle that is ready to be installed on a cluster, but it's not registered in any catalog yet. For doing this, the `olm` tool provides a utility to add new Operator bundle images to an existing catalog by doing:

```shell
opm index add --bundles $BUNDLE_IMAGE --tag $INDEX_IMAGE --build-tool docker --skip-tls
          
          
opm index add \
    --bundles <your container registry>/<your container registry namespace>/joke-manifest-bundle:<tag> \
    --tag <catalog container registry>/<catalog container registry namespace>/<catalog name>:<tag> \
    --build-tool docker --skip-tls
docker push <catalog container registry>/<catalog container registry namespace>/<catalog name>:<tag>
```

| If you're using an insecure registry, you'd need to append the argument `--skip-tls` to the `opm index` command.

The `<catalog container registry>/<catalog container registry namespace>/<catalog name>:<tag>` image is the catalog image that must be registered on the cluster. 
If you want to register a custom catalog, you can create it and configure it with the catalog image by doing the next command:

```shell
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: joke-operator
  namespace: operators
spec:
  sourceType: grpc
  image: <catalog container registry>/<catalog container registry namespace>/<catalog name>:<tag>
EOF
```

Once the catalog is installed, you should see the catalog pod up and running:

```shell
kubectl get pods -n operators --selector=olm.catalogSource=joke-operator
```

4. Install your operator via OLM

OLM deploys operators via [subscriptions](https://olm.operatorframework.io/docs/tasks/install-operator-with-olm/#install-your-operator). Creating a  `Subscription` will trigger the operator deployment. You can simply create the `Subscription` resource that contains the operator name and channel to install by running the following command:

```shell
cat <<EOF | kubectl create -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: joke-subscription
  namespace: <your namespace>
spec:
  channel: alpha
  name: joke
  source: joke-operator
  sourceNamespace: operators
EOF
```

Once the subscription is created, you should see your operator pod up and running:

```shell
kubectl get csv -n operators jokerequestreconciler
```

Also, this example needs the `Joke` CRD to be installed on the cluster:

```shell
kubectl apply -f src/main/k8s/jokes.samples.javaoperatorsdk.io-v1.yml -n <your namespace>
```

5. Test your operator

Deploy the test request (or your own): `kubectl apply -n <your namespace> -f src/main/k8s/jokerequest.yml` and,
if everything went well, a `Joke` resource named after the `id` of the joke retrieved from the API will be
  created on your cluster: `kubectl get jokes -n <your namespace>`.

### Native binary

To build a native binary for your platform, just run: `mvn package -Pnative`. The binary will be
found in the `target` directory.
