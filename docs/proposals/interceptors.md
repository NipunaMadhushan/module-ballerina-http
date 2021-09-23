# Proposal: HTTP Interceptors 

_Ownes_: Shafreen, Chamil, Ayesh, Tharmi
_Reviewers_: Chanaka
_Created_: 2021/09/23
_Updated_: 2021/09/23
_Issue_: [#692](https://github.com/ballerina-platform/ballerina-standard-library/issues/692)

## Summary 
Enhance the HTTP package with Interceptors. Interceptors typically do small units of work such as logging, header manipulation, state publishing, etc, before resources are invoked. 

## History 
> **Note**: 1.2.x versions used the filters for interceptors.

The 1.2.x version of HTTP packages did include filters support. Filters could be engaged both in the form of RequestFilter and ResponseFilter. As the names suggest RequestFilter are engaged for inbound requests whereas ResponseFilters are engaged for outbound responses. 

### RequestFilter

Following is an example of RequestFilter,

```ballerina
public type RequestFilter object {
   *http:RequestFilter;

   public function filterRequest(http:Caller caller, http:Request req, http:FilterContext context) returns boolean {
       req.setHeader("name_header", "header_value");
       return true;
   }
};
```
`filterRequest` function is invoked when there is an inbound request. It has three arguments Caller, Request and FilterContext. These arguments allow the user to modify the inbound request, complete the request/response cycle by sending back a response using the Caller or do some work and continue the filter chain by returning true. 

FilterContext allows the user to pass data between filters that includes both RequestFilters as well as ResponseFilters. For a given request/response cycle there is only one FilterContext. 

### ResponseFilter

Following is an example of ResponseFilter,

```ballerina
public type ResponseFilter object {
   *http:ResponseFilter;

   public function filterResponse(http:Response res, http:FilterContext context) returns boolean {
       res.setHeader("header_name", "header_value");
       return true;
   }
};
```
 
`filterResponse` function is invoked when there is an outbound response. It has only two arguments: Response and FilterContext. Because the response is already written using the Caller by the user there is no point in having a Caller in the argument list. Response argument allows users to modify the outbound response. To continue the filter chain filterResponse function must return `true`.
### Engaging Filters

Following code snippet shows how the Listener is configured with both Request and Response filters. 

```ballerina
listener http:Listener echoListener = new http:Listener(9090, config = {filters: [new RequestFilter(), new ResponseFilter()]});
```

### Execution Order of Filters
Listeners can be configured with an array of filters. Suppose http:Listener is configured with the below filter chain. RequestFilters are denoted in blue whereas Response filters are denoted in green. Numbers in the box represent array index values.

![Filters (2)](https://user-images.githubusercontent.com/6178058/132833551-8df6feef-d537-4077-9dbc-7baf5e81abd1.png)

In this the order of execution RequestFilters and ResponseFilters are as follows assuming that the request goes all the way to the resource.

RequestFilters: 0, 1, 3
ResponseFilters: 4, 2

Basically ResponseFilters are executed in the opposite direction of RequestFilters. In other words, RequestFilters are executed head to tail whereas ResponseFilters are executed tail to head.

> **Note**: However, if the user decides to respond at 3 and terminate the cycle, only the ResponseFilter at 2 gets executed. 

## Goals 
- Adapt the 1.2.x filters design to swan-lake as interceptors
- Introduce proper error handling 
- Introduce service level interceptors chain
- Introduce binding interceptors based on HTTP path and verb

## Motivation 
The ability to execute some common logic for all the inbound requests and outbound responses has proven to be highly useful. It typically includes some small unit of work such as the below.

- Logging 
- Header manipulation 
- Observability 
- Throttling 
- Validating 
- Securing 

Without interceptors such logic will have to be duplicated in each resource leading to less readable, maintainable and error-prone services. Of-course it is possible to abstract the logic into a function and invoke the function at the top of the resource but this again leads to aforementioned problems. One might forget to add it to all the resources in the service and it becomes even more difficult when there is more than one such function. Because the order of the functional call matters. Having different execution orders in each resource may not lead to expected consistent behavior.

Therefore, including interceptors support to HTTP services would definitely improve usability of the library. 

## Description  
As mentioned in the Goals and None-Goals section, the primary goal of this proposal is to adapt the 1.2.x version of the Filters into the swan-lake version as interceptors. Therefore, the most of the things discussed in the `History` section is applicable directly to the swan-lake design as well. 

The key attributes of the interceptors are as follows,
- Can execute any code
- Can trigger the next interceptor in the pipeline 
- Can end the request/response cycle 
- Make minor changes to the request/response objects 
- Can pass state from one interceptor to another

These attributes are similar to 1.2.x and there is no change. However, there are some syntax and semantics changes as described below.

### RequestInterceptor 
Following is an example of RequestInterceptor written in Ballerina swan-lake. RequestInterceptor can only have one resource function. 

```ballerina
service class RequestInterceptor {
   *http:RequestInterceptor;
 
   resource function 'default [string… path](http:RequestContext ctx, http:Caller caller,
                       http:Request req) returns RequestInterceptor|error? {
       // do some work
       return ctx.next();
   }
}

```
#### `resource` Functions 

Since interceptors work with network activities, it must be either a remote or resource function. In this case resource functions are used for RequestInterceptor as it gives more flexibility. With resource functions interceptors can be engaged based on HTTP method and path as well. 

For instance consider a scenario where there are two resources; one on path `foo` whereas the other on path `bar`. If the user writes a interceptor as follows, it would only get hit when the request is directed to `foo` resource. 

```ballerina
service class RequestInterceptor {
   *http:RequestInterceptor;
 
   resource function 'default foo(http:RequestContext ctx, http:Caller caller,
                       http:Request req) returns RequestInterceptor|error? {
       // do some work
       return ctx.next();
   }
}
```
However, if the user wants to write a interceptor which is not bound to any path, meaning the interceptor gets hit for requests directed to `foo` or `bar`, the user can do the following.

```ballerina
service class RequestInterceptor {
   *http:RequestInterceptor;
 
   remote function 'default [string… path](http:RequestContext ctx, http:Caller caller,
                       http:Request req) returns RequestInterceptor|error? {
       // do some work
       return ctx.next();
   }
}
```
Basically the rules are the same as what is there for the HTTP service dispatcher. 

#### Arguments 
The list of arguments are the same as the 1.2.x version. 

##### RequestContext 
Following is the rough definition of the RequestContext. 

```ballerina
public isolated class RequestContext {
    private final map<value:Cloneable|isolated object {}> attributes = {};

    public isolated function add(string 'key, value:Cloneable|isolated object {} value) {
        if value is value:Cloneable {
            lock {
                self.attributes['key] = value.clone();
            }
        }
        else {
            lock {
                self.attributes['key] = value;
            }   
        }
    }

    public isolated function get(string 'key) returns value:Cloneable|isolated object {} {
        lock {
            return self.attributes.get('key);
        }
    }

    public isolated function remove(string 'key) {
        lock {
            value:Cloneable|isolated object {} _ = self.attributes.remove('key);
        }
    }

    public isolated function next() returns RequestInterceptor|ResponseInterceptor|error? = external;
}
```

#### `next()` Method
However, there is an addition when it comes to RequestContext. A new method namely, `next()` is introduced to control the execution flow. Users must invoke `next()` method in order to get the next interceptor in the pipeline. Then the reference of the retrieved interceptor must be returned from resource function. Pipeline use this reference to execute the next interceptor.

> **Note**: Even if there is one RequestInterceptor in the pipeline, users must call `next()` in oder to dispatch to the final service which makes the final service also part of the pipeline.

Previously, this was controlled by returning a boolean value which is quite cryptic and confusing. 

#### Returning `error?`
Resource functions can return only `error?`. In the case of an error, intercetpor chain execution jumps to the nearest RequestErrorInterceptor in the chain. It is a special kind of interceptor which could be used to handle errors. More on this can be found under section `RequestErrorInterceptor and ResponseErrorInterceptor`.

However, in the case of there is no RequestErrorInterceptor in the pipeline, framework returns 500 internal server error response to the client.

### ResponseInterceptor
Following is an example of ResponseFilter written in Ballerina swan-lake. 

```ballerina
service class ResponseInterceptor {
   *http:ResponseInterceptor;
 
   remote function interceptResponse(http:RequestContext ctx, http:Response res) returns ResponseInterceptor|error? {
       // do some work
       return ctx.next();
   }
}
```
 
ResponseInterceptor is different from RequestInterceptor. Since it has nothing to do with HTTP methods and paths, remote functions are used instead of resource functions. Also, in the ResponseInterceptor there is no Caller argument for the same reasons explained in the `History` section.

#### Returning `error?`
`interceptResponse` function could return only `error?`. In the case of an error, interceptor execution jumps to the nearest ResponseErrorInterceptor. It is a special kind of interceptor which could be used to handle errors. More on this can be found under section `RequestErrorFilter and ResponseErrorFilter`.

However, in the case of there is no ResponseErrorInterceptor in the pipeline, framework returns 500 internal server error response to the client.

### RequestErrorInterceptor and ResponseErrorInterceptor

As mentioned above this is a special kind of interceptor designed to handle errors. These interceptors can be at any location of the pipeline.

```ballerina
service class RequestErrorInterceptor {
   *http:RequestErrorInterceptor;
 
   remote function 'default [string… path](http:RequestContext ctx, http:Caller caller,
                       http:Request req, error err) returns RequestInterceptor|error? {
       // deal with the error
       return ctx.next();
   }
}
```

As you may have noticed the only additional argument in this case is `error err`. 

The same works ResponseErrorInterceptor, the only difference is there is no Caller argument in the interceptResponseError method.

```ballerina
service class ResponseErrorInterceptor {
   *http:ResponseErrorInterceptor;
 
   remote function interceptResponseError(http:RequestContext ctx,
                       http:Response res, error err) returns ResponseInterceptor|error? {
       // deal with the error
       return ctx.next();
   }
}
```

In the case of an error returned within the ErrorInterceptor, again execution will jump to the nearest ErrorInterceptor. However, if there is no ErrorInterceptor to jump to, it resutls in 500 internal server error just like in interceptors. Besides, ErrorInterceptor is another interceptor.

### Engaging Interceptors

#### Service Level
Unlike in 1.2.x, swan-lake could engage interceptors at service level as well. One reason for this is that users may want to engage two different interceptor pipelines for each service even though it is attached to the same listener. At the service level resource function paths are relative to the service base path.

```ballerina
@http:ServiceConfig{
   filters: [requestFilter, responseFilter]
}
```

#### Listener Level
There is no difference between 1.2.x and swan-lake when it comes to engaging the interceptors at the listener level. At the listener level resource function paths are relative to the `/`. 

```ballerina
listener http:Listener echoListener = new http:Listener(9090, config = {filters: [requestFilter, responseFilter]});
```

> **Note**: Since HTTP Service configuration record includes a field of type Object, it no longer falls under `anydata`. This means you no longer can make the entire HTTP service configuration as a `configurable` variable. However, in reality you don’t need to make the entire configuration record configurable but rather a selective set which you can still do. The same applies for the HTTP Listener configuration.

### Execution Order of Filters
There is no difference between 1.2.x and swan-lake when it comes to the execution order of interceptors. However, with the exception of RequestErrorInterceptors and ResponseErrorInterceptors.

![NewFilters](https://user-images.githubusercontent.com/6178058/133388424-e22e36d4-e9ec-4264-ab3f-43c0e81e4073.jpg)

In the above example blue dashed box represents the RequestErrorInterceptor and blue boxes simply represent the RequestInterceptors whereas green dashed box represents the ResponseErrorInterceptor and green boxes simply represent the ResponseInterceptor. The new execution orders is as follows,
 
RequestInterceptor: 1, 2, 4
ResponseFilter: 5, 3, 0

However, consider the scenario where RequestInterceptor at two returns an error, in the case execution jumps from 2 to 5 as the nearest ErrorInterceptor is at 5. The same goes to the response path.
 
For more information on this read the section `Execution Order of Filters` under `History` section.

#### Service Annotations
Service annotations could include security validations related to the service. These security validations are only executed after executing all the request interceptors.  In other words security validations are implemented as the last interceptor in the pipeline. The same applies for other service level or resource level annotations. 

If the user wants to execute security as the first interceptor in the pipeline, the user could use imperative approach instead of declarative approach (which is annotation based). 

### Accessing RequestContext from the Resource
RequestContext can be accessed at the resource level as well. In that case users need to specify the RequestContext as part of the resource argument. Example,
```ballerina
resource function post foo(@http:Payload json accQuery, http:RequestContext ctx) returns json {}
```

> **Note**: Calling `ctx.next()` inside the resource function returns an error as there is no next RequestInterceptor to return.

### Concurrency 
Interceptors are instantiated only once. Therefore the state is preserved among multiple requests. However, this also means the same interceptor can and will execute concurrently. Since interceptor is a service object the Ballerina service concurrency rules apply here as well. Therefore, users must take special care when they write the logic.
 
### Cosmetic Additions
#### Data Binding 
Both RequestInterceptor and ResponseInterceptor methods support data binding. Which means users can directly access the payload, headers and query parameters. In order to get hold of the headers and the payload, users must use `@http:Payload` and `@http:Headers`. 

#### Return to Respond 
There is a key difference between interceptors and final service. Resources in the final service allow returning values which in turn results in HTTP responses. The same can be done inside the RequestInterceptors. However, as mentioned earlier RequestInterceptors additionally could return the next RequestInterceptor to continue the pipeline which doesn't translate into a HTTP response.