# MySQL Schema Operator

This example shows how an operator can control resources outside of the Kubernetes cluster. In this case it will be
managing MySQL schemas in an existing database server. This is a common scenario in many organizations where developers
need to create schemas for different applications and environments, but the database server itself is managed by a 
different team. Using this operator a dev team can create a CR in their namespace and have a schema provisioned automatically.
Access to the MySQL server is configured in the configuration of the operator, so admin access is restricted. 

This is an example input:
```yaml
apiVersion: "mysql.sample.javaoperatorsdk/v1"
kind: MySQLSchema
metadata:
  name: mydb
spec:
  encoding: utf8
```

Creating this custom resource will prompt the operator to create a schema named `mydb` in the MySQL server and update
the resource status with its URL. Once the resource is deleted, the operator will delete the schema. Obviously don't
use it as is with real databases. 

### Quick start

To quickly test your operator (and develop it), no need to create an image and deploy it to the
cluster. You can just follow the steps below to get started quickly:

- Connect to your cluster of choice using `kubectl/oc`, select the appropriate namespace/project.
  The operator will automatically connect to that cluster/namespace combination when started.
- Run `mvn install` on the parent directory to build the project locally.
- Run `mvn package` to build the operator. This will automatically generate several resources for
  you, in particular the CRDs associated with the custom resources we will be dealing with. These
  CRDs are generated in `target/kubernetes`.
- Deploy the CRDs to your cluster (requires cluster admin privileges, while this shouldn't be an
  issue for most "testing" clusters such as `minikube` or `kind`, you might need to log in to your
  OpenShift clusters with an admin account):
  ```sh
  kubectl apply -f target/kubernetes/mysqlschemas.mysql.sample.javaoperatorsdk-v1.yml
  ```           
- If you look at the application.properties, you will notice this is where the access to the MySQL server is configured.
  In dev mode, such a server is provided by [Quarkus DevService](https://quarkus.io/guides/dev-services).
- Launch the app in dev mode: `mvn quarkus:dev`
- Deploy the test schema (or your own): `kubectl apply -f src/main/resources/mydb.yml`
- The operator will take your request and attempt to create the schema. If everything
  went well, a database and user will be created on your mysql server.
- Clean up the database server by deleting the schema : `kubectl delete -f src/main/resources/mydb.yml`

### Cluster deployment

The extension is configured with the Quarkus [Kubernetes](https://quarkus.io/guides/deploying-to-kubernetes) and [Jib container image](https://quarkus.io/guides/container-image#jib) extensions so it
automatically generates deployment descriptors and image to deploy your operator on your cluster.
These files are found in their regular spots (`target/kubernetes` for the descriptors).

### Native binary

To build a native binary for your platform, just run: `mvn package -Pnative`. The binary will be
found in the `target` directory.
