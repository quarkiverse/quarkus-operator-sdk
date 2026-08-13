#!/usr/bin/env bash
# Removes an operator installed via installOperatorUsingOlm.sh. Must be run before
# installing another operator into the same namespace, otherwise OLM's resolver can
# treat the succeeded CSV as a conflict and block the next install (ConstraintsNotSatisfiable).
NAME="${1}"
K8S_NAMESPACE=operators
CATALOG_NAME="$NAME"-catalog
SUBSCRIPTION_NAME="$NAME"-subscription

installed_csv=$(kubectl get subscription "$SUBSCRIPTION_NAME" -n "$K8S_NAMESPACE" -o jsonpath='{.status.installedCSV}' 2>/dev/null || true)

kubectl delete subscription "$SUBSCRIPTION_NAME" -n "$K8S_NAMESPACE" --ignore-not-found
if [ -n "$installed_csv" ]; then
  kubectl delete clusterserviceversion "$installed_csv" -n "$K8S_NAMESPACE" --ignore-not-found
fi
kubectl delete catalogsource "$CATALOG_NAME" -n "$K8S_NAMESPACE" --ignore-not-found
