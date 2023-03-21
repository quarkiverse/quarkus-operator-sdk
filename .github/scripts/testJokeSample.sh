#!/usr/bin/env bash
NAMESPACE="${1}"

source .github/scripts/waitFor.sh

# Ensure the CRD is installed
waitFor crd $NAMESPACE jokerequests.samples.javaoperatorsdk.io

# Test operator by creating a Joke Request resource
kubectl apply -f samples/joke/src/main/k8s/jokerequest.yml

# And wait for the operator to create another Joke resource
waitFor joke $NAMESPACE NAME