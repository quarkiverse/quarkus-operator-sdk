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
- Run `mvn package` to build the operator. This will automatically generate several resources for
  you, in particular the CRDs associated with the custom resources we will be dealing with. These
  CRDs are generated in `target/classes/META-INF/fabric8` and come in two flavors: `v1`
  and `v1beta1` versions which correspond to the versions of the CRD spec. We recommend you use
  the `v1` version but might need to fall back to `v1beta1` if you're connecting to an older
  cluster.
- Deploy the CRDs to your cluster (requires cluster admin privileges, while this shouldn't be an
  issue for most "testing" clusters such as `minikube` or `kind`, you might need to log in to your
  OpenShift clusters with an admin account):
  ```sh
  kubectl apply -f target/classes/META-INF/fabric8/jokerequests.samples.javaoperatorsdk.io-v1.yml
  kubectl apply -f target/classes/META-INF/fabric8/jokes.samples.javaoperatorsdk.io-v1.yml
  ```           
- Launch the app in dev mode: `mvn quarkus:dev`
- Deploy the test request (or your own): `kubectl apply -f src/main/resources/jokerequest.yml`
- The operator will take your request and attempt to retrieve a joke from the api. If everything
  went well, a `Joke` resource named after the `id` of the joke retrieved from the API will be
  created on your cluster.
    - You can check the status of the request by doing something similar to, `jr` being the short
      name associated with `JokeRequest`:
      ````sh
      kubectl describe jr
      ````
    - You can check if your joke has been created and look for the id in the returned list:
      ```shell
      kubectl get jokes
      ```
    - Check your joke:
      ```shell
      kubectl get jokes <your joke id> -o jsonpath="{.joke}{'\n'}" 
      ```

### Cluster deployment

The extension is configured with the Quarkus Kubernetes and Jib container image extensions so it
automatically generates deployment descriptors and image to deploy your operator on your cluster.
These files are found in their regular spots (`target/kubernetes` for the descriptors).

### Native binary

To build a native binary for your platform, just run: `mvn package -Pnative`. The binary will be
found in the `target` directory.