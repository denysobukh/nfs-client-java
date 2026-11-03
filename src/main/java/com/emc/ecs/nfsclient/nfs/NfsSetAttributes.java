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
package com.emc.ecs.nfsclient.nfs;

import com.emc.ecs.nfsclient.rpc.Xdr;

/**
 * Used to set file or directory attributes, as specified by RFC 1813
 * (https://tools.ietf.org/html/rfc1813).
 */
public class NfsSetAttributes {

  /**
   * The protection mode bits, defined as follows:
   *
   * <ul>
   *   <li><strong>0x00800</strong> - Set user ID on execution.
   *   <li><strong>0x00400</strong> - Set group ID on execution.
   *   <li><strong>0x00200</strong> - Save swapped text (not defined in POSIX).
   *   <li><strong>0x00100</strong> - Read permission for owner.
   *   <li><strong>0x00080</strong> - Write permission for owner.
   *   <li><strong>0x00040</strong> - Execute permission for owner on a file. Or lookup (search)
   *       permission for owner in directory.
   *   <li><strong>0x00020</strong> - Read permission for group.
   *   <li><strong>0x00010</strong> - Write permission for group.
   *   <li><strong>0x00008</strong> - Execute permission for group on a file. Or lookup (search)
   *       permission for group in directory.
   *   <li><strong>0x00004</strong> - Read permission for others.
   *   <li><strong>0x00002</strong> - Write permission for others.
   *   <li><strong>0x00001</strong> - Execute permission for others on a file. Or lookup (search)
   *       permission for others in directory.
   * </ul>
   */
  private Long _mode; // uint32

  /** the user ID of the owner of the file. */
  private Long _uid; // uint32

  /** the group ID of the group of the file. */
  private Long _gid; // uint32

  /** the size of the file in bytes. */
  private Long _size; // uint64

  /** the time when the file data was last accessed. */
  private NfsTime _atime;

  /** the time when the file data was last modified. */
  private NfsTime _mtime;

  /**
   * Sets the protection mode bits to include in the request.
   *
   * @param mode The protection mode bits, defined as follows:
   *     <ul>
   *       <li><strong>0x00800</strong> - Set user ID on execution.
   *       <li><strong>0x00400</strong> - Set group ID on execution.
   *       <li><strong>0x00200</strong> - Save swapped text (not defined in POSIX).
   *       <li><strong>0x00100</strong> - Read permission for owner.
   *       <li><strong>0x00080</strong> - Write permission for owner.
   *       <li><strong>0x00040</strong> - Execute permission for owner on a file. Or lookup (search)
   *           permission for owner in directory.
   *       <li><strong>0x00020</strong> - Read permission for group.
   *       <li><strong>0x00010</strong> - Write permission for group.
   *       <li><strong>0x00008</strong> - Execute permission for group on a file. Or lookup (search)
   *           permission for group in directory.
   *       <li><strong>0x00004</strong> - Read permission for others.
   *       <li><strong>0x00002</strong> - Write permission for others.
   *       <li><strong>0x00001</strong> - Execute permission for others on a file. Or lookup
   *           (search) permission for others in directory.
   *     </ul>
   */
  public void setMode(Long mode) {
    _mode = mode;
  }

  /**
   * Sets the requested owner user ID.
   *
   * @param uid the user ID of the owner of the file.
   */
  public void setUid(Long uid) {
    _uid = uid;
  }

  /**
   * Sets the requested owner group ID.
   *
   * @param gid the group ID of the group of the file.
   */
  public void setGid(Long gid) {
    _gid = gid;
  }

  /**
   * Sets the requested file size in bytes.
   *
   * @param size the size of the file in bytes.
   */
  public void setSize(Long size) {
    _size = size;
  }

  /**
   * Sets the requested last-access time.
   *
   * @param atime the time when the file data was last accessed.
   */
  public void setAtime(NfsTime atime) {
    _atime = checkSettingTime(atime, "atime");
  }

  /**
   * Sets the requested last-modification time.
   *
   * @param mtime the time when the file data was last modified.
   */
  public void setMtime(NfsTime mtime) {
    _mtime = checkSettingTime(mtime, "mtime");
  }

  /** Default constructor */
  public NfsSetAttributes() {
    this(null, null, null, null, NfsTime.DO_NOT_CHANGE, NfsTime.DO_NOT_CHANGE);
  }

  /** Convenient constructor for multiple changes. */
  public NfsSetAttributes(Long mode, Long uid, Long gid, NfsTime atime, NfsTime mtime) {
    this(mode, uid, gid, null, atime, mtime);
  }

  /** Convenient constructor for multiple changes. */
  public NfsSetAttributes(Long mode, Long uid, Long gid, Long size, NfsTime atime, NfsTime mtime) {
    _mode = mode;
    _uid = uid;
    _gid = gid;
    _size = size;
    _atime = checkSettingTime(atime, "atime");
    _mtime = checkSettingTime(mtime, "mtime");
  }

  private static NfsTime checkSettingTime(NfsTime time, String name) {
    if (time != null && time.isBareTime()) {
      throw new IllegalArgumentException("The " + name + " must be a time setting time");
    }
    return time;
  }

  /** Set Xdr fields for the rpc call. */
  public void marshalling(Xdr xdr) {
    marshalling(xdr, _mode);
    marshalling(xdr, _uid);
    marshalling(xdr, _gid);
    if (_size != null) {
      xdr.putBoolean(true);
      xdr.putLong(_size.longValue());
    } else {
      xdr.putBoolean(false);
    }
    marshalling(xdr, _atime);
    marshalling(xdr, _mtime);
  }

  private void marshalling(Xdr xdr, NfsTime nfsTime) {
    if (nfsTime != null) {
      nfsTime.marshalling(xdr);
    } else {
      xdr.putInt(0);
    }
  }

  private static void marshalling(Xdr xdr, Long longValue) {
    if (longValue != null) {
      xdr.putBoolean(true);
      xdr.putUnsignedInt(longValue.longValue());
    } else {
      xdr.putBoolean(false);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(" [mode :").append(_mode);
    sb.append(" uid: ").append(_uid);
    sb.append(" gid: ").append(_gid);
    sb.append(" size: ").append(_size);
    sb.append(" atime: ").append(_atime);
    sb.append(" mtime: ").append(_mtime);
    sb.append("]");

    return sb.toString();
  }
}
