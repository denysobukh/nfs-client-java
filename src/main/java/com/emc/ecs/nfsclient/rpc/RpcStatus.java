/**
 * Copyright 2016-2018 Dell Inc. or its subsidiaries. All rights reserved.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
 * except in compliance with the License. A copy of the License is located at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * <p>or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.emc.ecs.nfsclient.rpc;

/**
 * @author seibed
 */
public class RpcStatus implements java.io.Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * Unable to bind to a privileged port as requested. NOT specified by RFC 1831
   * (https://tools.ietf.org/html/rfc1831).
   */
  public static final RpcStatus LOCAL_BINDING_ERROR = new RpcStatus(-1001, "LOCAL_BINDING_ERROR");

  /**
   * Network error blocking RFC request or response. NOT specified by RFC 1831
   * (https://tools.ietf.org/html/rfc1831).
   */
  public static final RpcStatus NETWORK_ERROR = new RpcStatus(-1001, "NETWORK_ERROR");

  /**
   * @return The int status value.
   */
  public final int getValue() {
    return _value;
  }

  /** The int status value. */
  private final int _value;

  private final String _name;

  /**
   * Create the instance from the int status value.
   *
   * @param value
   */
  protected RpcStatus(int value) {
    this(value, null);
  }

  private RpcStatus(int value, String name) {
    _value = value;
    _name = name;
  }

  protected Object readResolve() {
    if ("LOCAL_BINDING_ERROR".equals(_name)) {
      return LOCAL_BINDING_ERROR;
    }
    if ("NETWORK_ERROR".equals(_name)) {
      return NETWORK_ERROR;
    }
    return this;
  }

  @Override
  public String toString() {
    if (this == LOCAL_BINDING_ERROR) {
      return "LOCAL_BINDING_ERROR(" + _value + ")";
    }
    if (this == NETWORK_ERROR) {
      return "NETWORK_ERROR(" + _value + ")";
    }
    return "RpcStatus:" + _value;
  }
}
