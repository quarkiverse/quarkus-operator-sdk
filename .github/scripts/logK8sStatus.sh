#!/usr/bin/env bash
# Dumps OLM/operator state useful for troubleshooting a failed OLM sample install.
echo "Pod statuses in olm namespace:"
kubectl get pod -n olm
echo ""
echo "------------------------------"
echo "Subscriptions:"
kubectl get subs -n operators -o yaml
echo ""
echo "------------------------------"
echo "Controllers:"
kubectl get pod -n operators
echo ""
echo "------------------------------"
echo "Events:"
kubectl get events -n operators
echo ""
echo "------------------------------"
