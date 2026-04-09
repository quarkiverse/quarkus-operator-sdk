#!/bin/sh
#---------------------------------------------------
# Hook invoked by the Quarkus Ecosystem CI workflow
#---------------------------------------------------

# Set environment variables
{
  echo "NAMESPACE_FROM_ENV=fromEnvVarNS"
  echo "QUARKUS_OPERATOR_SDK_CONTROLLERS_EMPTY_NAMESPACES=fromEnv1,fromEnv2"
  echo "VARIABLE_NS_ENV=variableNSFromEnv"
} >> $GITHUB_ENV
