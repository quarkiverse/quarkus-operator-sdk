#!/usr/bin/env bash
# usage:
# ./waitFor.sh joke operators Succeeded
# ./waitFor.sh pod operators Running "name -o jsonpath='{.status.phase}'"

RESOURCE="${1}"
NAMESPACE="${2}"
EXPECTED="${3}"
EXTRA="${4-}"

retries=30
until [[ $retries == 0 ]]; do
  actual=$(kubectl get $RESOURCE -n $NAMESPACE $EXTRA 2>/dev/null || echo "Waiting for $RESOURCE -> $EXPECTED to appear")
  if [[ "$actual" =~ .*"$EXPECTED".* ]]; then
    echo "Resource \"$RESOURCE\" found with: $actual" 2>&1
    break
  else
    echo "Waiting for resource \"$RESOURCE\" actual: $actual" 2>&1
  fi
  sleep 10s
  retries=$((retries - 1))
done

if [[ $retries == 0 ]]; then
  echo "Failed to get $RESOURCE" 2>&1
  exit 1
else
  exit 0
fi