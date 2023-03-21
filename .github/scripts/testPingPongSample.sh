NAMESPACE="${1}"

# Ensure the CRD is installed
.github/scripts/waitFor.sh crd $NAMESPACE pings.samples.javaoperatorsdk.io

# Test operator by creating a Joke Request resource
kubectl apply -f samples/pingpong/src/main/k8s/pingrequest.yml

# The ping reconciler should mark the ping resource as processed
.github/scripts/waitFor.sh ping $NAMESPACE PROCESSED "my-ping-request -o jsonpath='{.status.state}'"

# And the pong reconciler should mark the new pong resource as processed
.github/scripts/waitFor.sh pong $NAMESPACE PROCESSED "my-ping-request-pong -o jsonpath='{.status.state}'"