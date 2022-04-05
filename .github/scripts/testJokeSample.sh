NAMESPACE="${1}"

# Apply "3rd-party" CRD: joke is not an owned resource by our operator, so it's not generated
kubectl apply -f samples/joke/src/main/k8s/jokes.samples.javaoperatorsdk.io-v1.yml

# Test operator by creating a Joke Request resource
kubectl apply -f samples/joke/src/main/k8s/jokerequest.yml

# And wait for the operator to create another Joke resource
.github/scripts/waitFor.sh joke $NAMESPACE NAME