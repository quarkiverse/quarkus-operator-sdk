#!/usr/bin/env bash
NAMESPACE="${1}"

# Ensure the CRD is installed
if ! .github/scripts/waitFor.sh crd $NAMESPACE jokerequests.samples.javaoperatorsdk.io; then
  echo "CRD not installed: cannot run the Joke operator"
  exit 1;
fi

# Test operator by creating a Joke Request resource
kubectl apply -f samples/joke/src/main/k8s/jokerequest.yml

# And wait for the operator to create another Joke resource
if ! .github/scripts/waitFor.sh joke $NAMESPACE NAME; then
  echo "Joke was not properly created in response to request"
  exit 1;
fi

exit 0;