/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.http.compiler.codeaction;

import io.ballerina.stdlib.http.compiler.Constants;
import io.ballerina.stdlib.http.compiler.HttpDiagnosticCodes;

/**
 * CodeAction to add the remote method for response/ response error interceptor.
 */
public class AddInterceptorRemoteMethodCodeAction extends AddInterceptorMethodCodeAction {
    @Override
    protected String diagnosticCode() {
        return HttpDiagnosticCodes.HTTP_135.getCode();
    }

    @Override
    protected String methodKind() {
        return Constants.REMOTE_KEYWORD;
    }

    @Override
    protected String methodSignature(boolean isErrorInterceptor) {
        String method = "\n\tremote function interceptResponse";
        method += isErrorInterceptor ? "Error(error err, " : "(";
        method += "http:RequestContext ctx) returns http:NextService|error? {\n" +
                "\t\t// add your logic here\n" +
                "\t\treturn ctx.next();\n" +
                "\t}\n";
        return method;
    }

    @Override
    public String name() {
        return "ADD_INTERCEPTOR_REMOTE_METHOD";
    }
}
