import {QwcHotReloadElement, css, html} from 'qwc-hot-reload-element';
import {JsonRpc} from 'jsonrpc';
import '@vaadin/details';
import '@vaadin/list-box';
import '@vaadin/item';
import '@vaadin/horizontal-layout';
import '@vaadin/vertical-layout';
import '@vaadin/icon';
import '@vaadin/vaadin-lumo-styles/vaadin-iconset.js'
import '@vaadin/form-layout';
import '@vaadin/text-field';
import 'qui-badge';

export class QWCQOSDKControllers extends QwcHotReloadElement {

  jsonRpc = new JsonRpc(this);

  constructor() {
    super();
  }

  connectedCallback() {
    super.connectedCallback();
    this.hotReload();
  }

  hotReload() {
    this.jsonRpc.getControllers().then(
        jsonRpcResponse => this._controllers = jsonRpcResponse.result);
  }

  static properties = {
    _controllers: {state: true},
  }

  render() {
    if (this._controllers) {
      return html`
        <vaadin-details opened>
          <vaadin-details-summary slot="summary">
            <qui-badge level="contrast">Configured controllers (${this._controllers.length})</qui-badge>
          </vaadin-details-summary>
          <vaadin-vertical-layout style="align-items: stretch;" theme="spacing-s padding-s">
            ${this._controllers.map(this.controller, this)}
          </vaadin-vertical-layout>
        </vaadin-details>
      `
    }
  }

  controller(controller) {
    return html`
      <vaadin-details theme="filled">
        <vaadin-details-summary slot="summary">
          ${nameImplAndResource(controller.name, controller.className,
              controller.resourceClass)}
        </vaadin-details-summary>
        <vaadin-vertical-layout theme="spacing-s">
          ${this.namespaces(controller)}
          <vaadin-horizontal-layout theme="spacing-s">
            ${this.children(controller.dependents, "Dependents",
                this.dependent)}
            ${this.children(controller.eventSources, "Event Sources",
                this.eventSource)}
          </vaadin-horizontal-layout>
        </vaadin-vertical-layout>
      </vaadin-details>`
  }

  namespaces(controller) {
    return html`<vaadin-details summary="Namespaces" theme="filled">
      <vaadin-vertical-layout>
        <vaadin-horizontal-layout theme="spacing-xs">
          <span>Configured:</span>
          ${controller.configuredNamespaces.map(ns => html`${name(ns)}`)}
        </vaadin-horizontal-layout>
         <vaadin-horizontal-layout theme="spacing-xs">
           <span>Effective:</span>
           ${controller.effectiveNamespaces.map(ns => html`${name(ns)}`)}
         </vaadin-horizontal-layout>
      </vaadin-vertical-layout>
    </vaadin-details>`
  }

  eventSource(eventSource) {
    return html`
      ${nameImplAndResource(eventSource.name, eventSource.type, eventSource.resourceClass)}
    `
  }

  dependent(dependent) {
    return html`
      <vaadin-details theme="filled">
        <vaadin-details-summary slot="summary">
          ${nameImplAndResource(dependent.name, dependent.type,
              dependent.resourceClass)}
        </vaadin-details-summary>
        <vaadin-vertical-layout>
          ${dependsOn(dependent)}
          ${conditions(dependent)}
        </vaadin-vertical-layout>
      </vaadin-details>`
  }

  children(children, childrenName, childRenderer) {
    if (children) {
      let count = children.length;
      return html`
        <vaadin-details theme="filled" summary="${count} ${childrenName}">
          <vaadin-vertical-layout>
            ${children.map((child) => html`${childRenderer(child)}`)}
          </vaadin-vertical-layout>
        </vaadin-details>
      `
    }
  }

}

function name(name) {
  return html`
    <qui-badge>${name}</qui-badge>`
}

function nameImplAndResource(n, impl, resource) {
  return html`
    <vaadin-vertical-layout>
      ${name(n)}
      <vaadin-horizontal-layout theme="spacing-xs">
        <vaadin-icon icon="lumo:arrow-right"></vaadin-icon>
        ${clazz(impl)}
        ${resourceClass(resource)}
      </vaadin-horizontal-layout>
    </vaadin-vertical-layout>`
}

const fabric8Prefix = 'io.fabric8.kubernetes.api.model.';
const josdkProcessingPrefix = 'io.javaoperatorsdk.operator.processing.';
const userProvided = 'success';
const k8sProvided = 'warning';
const josdkProvided = 'contrast';

function resourceClass(resourceClass) {
  return clazz(resourceClass, true)
}

function clazz(impl, isPill) {
  if (impl) {
    let level = userProvided;
    if (impl.startsWith(fabric8Prefix)) {
      level = k8sProvided;
      impl = impl.substring(fabric8Prefix.length);
    } else if (impl.startsWith(josdkProcessingPrefix)) {
       level = josdkProvided;
       impl = impl.substring(josdkProcessingPrefix.length);
    }
    return isPill ?
        html`<qui-badge level="${level}" small pill>${impl}</qui-badge>` :
        html`<qui-badge level="${level}" small>${impl}</qui-badge>`
  }
}

function conditions(dependent) {
  let hasConditions = dependent.hasConditions;
  if (hasConditions) {
    return html`
      <vaadin-details summary="Conditions">
        <vaadin-vertical-layout>
          ${field(dependent.readyCondition, "Ready")}
          ${field(dependent.reconcileCondition, "Reconcile")}
          ${field(dependent.deleteCondition, "Delete")}
        </vaadin-vertical-layout>
      </vaadin-details>
    `
  }
}

function dependsOn(dependent) {
   if (dependent.dependsOn && dependent.dependsOn.length > 0) {
     return html`
       <vaadin-details summary="Depends on">
         <vaading-vertical-layout>
           ${dependent.dependsOn.map(dep => html`${name(dep)}`)}
         </vaading-vertical-layout>
       </vaadin-details>
     `
   }
}

function field(value, label) {
  if (value) {
    return html`
      <vaadin-horizontal-layout>
        <span>${label}</span>: ${clazz(value)}
      </vaadin-horizontal-layout>`
  }
}

customElements.define('qwc-qosdk-controllers', QWCQOSDKControllers);