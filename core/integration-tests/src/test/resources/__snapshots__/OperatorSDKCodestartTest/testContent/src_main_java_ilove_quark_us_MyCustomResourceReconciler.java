package ilove.quark.us;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public class MyCustomResourceReconciler implements Reconciler<MyCustomResource> {

  @Override
  public UpdateControl<MyCustomResource> reconcile(MyCustomResource myCustomResource,
      Context<MyCustomResource> context) throws Exception {
    // implement reconciliation logic
    return UpdateControl.noUpdate();
  }
}