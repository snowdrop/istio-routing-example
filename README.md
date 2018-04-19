# Description

This booster showcases using Istio's A/B Testing capabilities with a set of example applications configured with appropriate routing and rules.

The primary objectives are:
 * Deploy a client UI application (client-service-consumer) and a load-balanced service (my-service) with two pods running different implementations (versions): service-a, and service-b.
 * Demonstrate creation of an Istio Ingress rule to expose the client UI.
 * Demonstrate that pods are load-balanced with equal weights by default, using OpenShift's default service clustering mechanism.
 * Demonstrate that Istio can be used to dynamically re-balance deployed pods in a service using a RouteRule.

# User Problem

In a microservice topology (MST), the problem of managing upgrades and rollouts of new functionality, or optimizing and incrementally testing alternate implementations becomes increasingly complex due to the increased complexity of the system as a whole. E.g. The more services participate in a microservices architecture, the most likely it will be that services will need to be independently upgraded and patched - this results in parts of the microservice architecture being unavailable unless traffic can be routed to alternate endpoints dynamically. The operations processes required to take down the entire system are not practical for systems beyond a simple design.

During upgrades/rollouts of any given individual service in an MST, the goal of an A/B deployment (or A/B test) is to deploy two versions (A and B) of a service in parallel, then divert traffic either incrementally or via a cutover to the new version of the service once it becomes fully available. The older version remains running in case of deployment failure, and traffic can be restored to its original pattern if errors are detected.

There are several benefits to this integration/deployment pattern. This prevents outage windows, and makes avoiding “the all night deployment” possible because updates can more reliably occur during normal working hours without disrupting system function. Due to the ease with which traffic can be re-routed, A/B routing also helps limit the duration of outages due to system failure that would traditionally only be restored by rolling back the newly deployed service to the prior version entirely.

# Concepts and Architectural Patterns

* Istio
* A/B deployments
* Application routing
* Runtime configuration changes
* Hot deployment
* Version control

# Prerequisites
* Minishift is installed and running on a the developer's environment, or the developer has access to an OpenShift instance that meets all prerequisites.
* Developer has installed Istio 0.7.0 onto the Minishift/OpenShift instance.
* Developer is logged in to Minishift/OpenShift with the Admin user.

# Use Case

First, create a new project on the cluster instance and grant service account privileges enabling its pods to access features not available to normal (restricted) pods. Then ensure that the istio-ingress service has been exposed --- this enables us to access our applications via the Istio system, and is the first step to using Istio in any project with externally facing services.

```bash
oc new-project demo-istio
oc adm policy add-scc-to-user privileged -z default -n demo-istio
oc label namespace demo-istio istio-injection=enabled
oc expose svc istio-ingress -n istio-system
```

### Build locally using Fabric8 Maven Plugin

Next, build and deploy the application using the Fabric8 Maven Plugin (FMP). Configuration for the FMP may be found both in pom.xml and `src/main/fabric8` files/folders. This configuration is used to define service names and deployments that control how pods are labeled/versioned on the OpenShift cluster. Labels and versions are key concepts for creating A/B testing or multi-versioned pods in a service.

```bash
mvn clean package fabric8:deploy -Popenshift
```

### Build on OpenShift using s2i
```bash
find . | grep openshiftio | grep application | xargs -n 1 oc apply -f

oc new-app --template=spring-boot-istio-ab-tests-booster-client-service-consumer -p SOURCE_REPOSITORY_URL=https://github.com/snowdrop/spring-boot-istio-ab-testing-booster -p SOURCE_REPOSITORY_REF=master -p SOURCE_REPOSITORY_DIR=client-service-consumer
oc new-app --template=spring-boot-istio-ab-tests-booster-service-a -p SOURCE_REPOSITORY_URL=https://github.com/snowdrop/spring-boot-istio-ab-testing-booster -p SOURCE_REPOSITORY_REF=master -p SOURCE_REPOSITORY_DIR=service-a
oc new-app --template=spring-boot-istio-ab-tests-booster-service-b -p SOURCE_REPOSITORY_URL=https://github.com/snowdrop/spring-boot-istio-ab-testing-booster -p SOURCE_REPOSITORY_REF=master -p SOURCE_REPOSITORY_DIR=service-b
```

## Expose the application UI for HTTP traffic

Create a route rule to properly forward traffic to the demo application. This is only necessary if your application accepts traffic at a different port/url than the default. In this case, our application accepts traffic at '/', but we will access it with the path '/example'.

```bash
oc create -f rules/client-route-rule.yml  
```

## Access the example application

Finally, access the application via the istio-system istio-ingress application URL. Run this command to determine the appropriate URL to access our demo. Make sure you access the url with the HTTP scheme. HTTPS is NOT enabled by default:

### For Minishift

```bash
echo http://$(minishift openshift service istio-ingress -n istio-system --url)/example/
```
### For a hosted OpenShift cluster

```bash
echo http://$(oc get route istio-ingress -o jsonpath='{.spec.host}{"\n"}' -n istio-system)/example/
```

## Understand the example

Now it's time to take a look at what this application does, and how it works.

Click "Invoke Service" in the client UI; do this several times. You will notice that the services are currently load-balanced at exactly 50%. This is not always desireable for an A/B deployment. Sometimes it is important to slowly direct traffic to a new service over time. In this case, we can supply an Istio RouteRule to control load balancing behavior:

```bash
oc create -f rules/ab-test-rule.yml
```

Note that the RouteRule defined in the file above uses labels "a" and "b" to identify each unique version of the service. If multiple services match any of these labels, traffic will be divided between them accordingly. Additional routes/weights can be supplied using additional labels/service versions as desired.

Click "Invoke Service" in the client UI; do this several times. You will notice that traffic is no longer routed at 50/50%, and more traffic is directed to service version B than service version A. Adjust the weights in the rule-file and re-run the command above. You should see traffic adjust accordingly.

Congratulations! You now know how to direct traffic between different versions of a service using Istio RouteRules.
