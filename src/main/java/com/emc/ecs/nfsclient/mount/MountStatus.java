/**
 * Copyright 2016-2018 Dell Inc. or its subsidiaries. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.emc.ecs.nfsclient.mount;

import java.util.HashMap;
import java.util.Map;

/**
 * Holder for the mount status values, as specified by RFC 1813
 * (https://tools.ietf.org/html/rfc1813).
 * 
 * @author seibed
 */
public class MountStatus {

    /**
     * No error.
     */
    public static final MountStatus MNT3_OK = new MountStatus(0, "MNT3_OK");

    /**
     * Not owner.
     */
    public static final MountStatus MNT3ERR_PERM = new MountStatus(1, "MNT3ERR_PERM");

    /**
     * No such file or directory.
     */
    public static final MountStatus MNT3ERR_NOENT = new MountStatus(2, "MNT3ERR_NOENT");

    /**
     * I/O error.
     */
    public static final MountStatus MNT3ERR_IO = new MountStatus(5, "MNT3ERR_IO");

    /**
     * Permission denied.
     */
    public static final MountStatus MNT3ERR_ACCES = new MountStatus(13, "MNT3ERR_ACCES");

    /**
     * Not a directory.
     */
    public static final MountStatus MNT3ERR_NOTDIR = new MountStatus(20, "MNT3ERR_NOTDIR");

    /**
     * Invalid argument.
     */
    public static final MountStatus MNT3ERR_INVAL = new MountStatus(22, "MNT3ERR_INVAL");

    /**
     * Filename too long.
     */
    public static final MountStatus MNT3ERR_NAMETOOLONG = new MountStatus(63, "MNT3ERR_NAMETOOLONG");

    /**
     * Operation not supported.
     */
    public static final MountStatus MNT3ERR_NOTSUPP = new MountStatus(10004, "MNT3ERR_NOTSUPP");

    /**
     * A failure on the server.
     */
    public static final MountStatus MNT3ERR_SERVERFAULT = new MountStatus(10006, "MNT3ERR_SERVERFAULT");

    /**
     * The known values.
     */
    private static final Map<Integer, MountStatus> VALUES = new HashMap<Integer, MountStatus>();
    static {
        addValues(new MountStatus[] { MNT3_OK, MNT3ERR_PERM, MNT3ERR_NOENT, MNT3ERR_IO, MNT3ERR_ACCES, MNT3ERR_NOTDIR,
                MNT3ERR_INVAL, MNT3ERR_NAMETOOLONG, MNT3ERR_NOTSUPP, MNT3ERR_SERVERFAULT });
    }

    /**
     * Convenience function to get the instance from the int status value.
     * 
     * @param value
     *            The int status value.
     * @return The instance.
     */
    public static MountStatus fromValue(int value) {
        MountStatus mountStatus = VALUES.get(value);
        if (mountStatus == null) {
            mountStatus = new MountStatus(value);
            VALUES.put(value, mountStatus);
        }
        return mountStatus;
    }

    /**
     * @param values
     *            Instances to add.
     */
    private static void addValues(MountStatus[] values) {
        for (MountStatus value : values) {
            VALUES.put(value.getValue(), value);
        }
    }

    /**
     * @return The int status value.
     */
    public int getValue() {
        return _value;
    }

    /**
     * The int status value.
     */
    private int _value;

    private final String _name;

    /**
     * Creates the mount status instances, as specified by RFC 1813
     * (https://tools.ietf.org/html/rfc1813).
     * 
     * @param value
     *            The int status value.
     */
    private MountStatus(int value) {
        this(value, "UNKNOWN_MOUNTSTATUS");
    }

    private MountStatus(int value, String name) {
        _value = value;
        _name = name;
    }

    @Override
    public String toString() {
        return _name + "(" + _value + ")";
    }
}
