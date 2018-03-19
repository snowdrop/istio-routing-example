## Purpose 

Showcase Istio's A/B Testing capabilities with a set of example applications configured with appropriate routing and rules.

## Deploy on Minishift

```bash
    $ oc new-project demo-istio
    $ oc adm policy add-scc-to-user privileged -z default -n demo-istio
    $ mvn clean package fabric8:deploy -Pistio-openshift
    $ oc expose svc istio-ingress -n istio-system
    $ oc create -f rules/route-rule-redir.yml    
    $ open $(minishift openshift service istio-ingress -n istio-system --url)/suggest/
```
