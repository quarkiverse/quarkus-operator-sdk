NAMESPACE="${1}"

# Test operator by creating a Joke Request resource
kubectl apply -f samples/joke/src/main/k8s/jokerequest.yml

# And wait for the operator to create another Joke resource
.github/scripts/waitFor.sh joke $NAMESPACE NAME