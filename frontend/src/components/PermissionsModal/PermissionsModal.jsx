import React, { useState, useEffect, useCallback } from 'react';
import { permissionApi, adminApi } from '../../services/api';
import './PermissionsModal.css';

const PERMISSION_TYPES = ['VIEW', 'EDIT', 'DELETE', 'MANAGE_PERMISSIONS', 'FULL_ACCESS'];

const PermissionsModal = ({ pageId, pageTitle, onClose }) => {
    const [permissions, setPermissions] = useState([]);
    const [isSensitive, setIsSensitive] = useState(false);
    const [users, setUsers] = useState([]);
    const [roles, setRoles] = useState([]);
    const [grantType, setGrantType] = useState('user'); // 'user' or 'role'
    const [selectedId, setSelectedId] = useState('');
    const [selectedPermission, setSelectedPermission] = useState('VIEW');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionLoading, setActionLoading] = useState(false);

    const loadData = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);
            const [perms, sensitive, userList, roleList] = await Promise.all([
                permissionApi.getPagePermissions(pageId),
                permissionApi.isSensitive(pageId),
                adminApi.getUsers(),
                adminApi.getRoles(),
            ]);
            setPermissions(perms);
            setIsSensitive(sensitive);
            setUsers(userList);
            setRoles(roleList);
        } catch (err) {
            setError('Failed to load permission data');
            console.error(err);
        } finally {
            setLoading(false);
        }
    }, [pageId]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const handleToggleSensitive = async () => {
        try {
            setActionLoading(true);
            if (isSensitive) {
                await permissionApi.markPublic(pageId);
            } else {
                await permissionApi.markSensitive(pageId);
            }
            setIsSensitive(!isSensitive);
        } catch (err) {
            alert('Failed to update sensitive status');
            console.error(err);
        } finally {
            setActionLoading(false);
        }
    };

    const handleGrant = async () => {
        if (!selectedId) {
            alert('Please select a ' + grantType);
            return;
        }
        try {
            setActionLoading(true);
            if (grantType === 'user') {
                await permissionApi.grantUserPermission(pageId, selectedId, selectedPermission);
            } else {
                await permissionApi.grantRolePermission(pageId, selectedId, selectedPermission);
            }
            await loadData();
            setSelectedId('');
        } catch (err) {
            alert('Failed to grant permission: ' + (err.response?.data?.message || err.message));
            console.error(err);
        } finally {
            setActionLoading(false);
        }
    };

    const handleRevoke = async (perm) => {
        try {
            setActionLoading(true);
            if (perm.userId) {
                await permissionApi.revokeUserPermission(pageId, perm.userId, perm.permissionType);
            } else if (perm.roleId) {
                await permissionApi.revokeRolePermission(pageId, perm.roleId, perm.permissionType);
            }
            await loadData();
        } catch (err) {
            alert('Failed to revoke permission');
            console.error(err);
        } finally {
            setActionLoading(false);
        }
    };

    const handleRevokeAll = async () => {
        if (!window.confirm('Remove all permissions from this page? It will become publicly accessible.')) return;
        try {
            setActionLoading(true);
            await permissionApi.revokeAllPermissions(pageId);
            await loadData();
        } catch (err) {
            alert('Failed to revoke all permissions');
            console.error(err);
        } finally {
            setActionLoading(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal permissions-modal" onClick={(e) => e.stopPropagation()}>
                <h2>Permissions: {pageTitle}</h2>

                {loading ? (
                    <div className="permissions-loading">Loading...</div>
                ) : error ? (
                    <div className="permissions-error">{error}</div>
                ) : (
                    <>
                        {/* Sensitive toggle */}
                        <div className="permissions-section">
                            <div className="sensitive-row">
                                <span className="sensitive-label">
                                    Page is <strong>{isSensitive ? 'Sensitive (Restricted)' : 'Public'}</strong>
                                </span>
                                <button
                                    className={`btn btn-sm ${isSensitive ? 'btn-warning' : 'btn-secondary'}`}
                                    onClick={handleToggleSensitive}
                                    disabled={actionLoading}
                                >
                                    {isSensitive ? 'Make Public' : 'Mark Sensitive'}
                                </button>
                            </div>
                        </div>

                        {/* Current permissions */}
                        <div className="permissions-section">
                            <h3>Current Permissions</h3>
                            {permissions.length === 0 ? (
                                <p className="no-permissions">No explicit permissions set.</p>
                            ) : (
                                <table className="permissions-table">
                                    <thead>
                                        <tr>
                                            <th>Target</th>
                                            <th>Type</th>
                                            <th>Permission</th>
                                            <th></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {permissions.map((perm) => (
                                            <tr key={perm.id}>
                                                <td>
                                                    {perm.username
                                                        ? `User: ${perm.username}`
                                                        : `Role: ${perm.roleName}`}
                                                </td>
                                                <td>{perm.userId ? 'User' : 'Role'}</td>
                                                <td>{perm.permissionType}</td>
                                                <td>
                                                    <button
                                                        className="btn btn-sm btn-danger"
                                                        onClick={() => handleRevoke(perm)}
                                                        disabled={actionLoading}
                                                    >
                                                        Revoke
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            )}
                            {permissions.length > 0 && (
                                <button
                                    className="btn btn-sm btn-danger remove-all-btn"
                                    onClick={handleRevokeAll}
                                    disabled={actionLoading}
                                >
                                    Remove All Permissions
                                </button>
                            )}
                        </div>

                        {/* Grant form */}
                        <div className="permissions-section">
                            <h3>Grant Permission</h3>
                            <div className="grant-form">
                                <div className="grant-row">
                                    <label className="grant-radio">
                                        <input
                                            type="radio"
                                            name="grantType"
                                            value="user"
                                            checked={grantType === 'user'}
                                            onChange={() => { setGrantType('user'); setSelectedId(''); }}
                                        />
                                        User
                                    </label>
                                    <label className="grant-radio">
                                        <input
                                            type="radio"
                                            name="grantType"
                                            value="role"
                                            checked={grantType === 'role'}
                                            onChange={() => { setGrantType('role'); setSelectedId(''); }}
                                        />
                                        Role
                                    </label>
                                </div>
                                <div className="grant-row">
                                    <select
                                        value={selectedId}
                                        onChange={(e) => setSelectedId(e.target.value)}
                                        className="grant-select"
                                    >
                                        <option value="">Select {grantType}...</option>
                                        {grantType === 'user'
                                            ? users.map((u) => (
                                                <option key={u.id} value={u.id}>
                                                    {u.username}{u.displayName ? ` (${u.displayName})` : ''}
                                                </option>
                                            ))
                                            : roles.map((r) => (
                                                <option key={r.id} value={r.id}>
                                                    {r.name}{r.description ? ` - ${r.description}` : ''}
                                                </option>
                                            ))}
                                    </select>
                                    <select
                                        value={selectedPermission}
                                        onChange={(e) => setSelectedPermission(e.target.value)}
                                        className="grant-select"
                                    >
                                        {PERMISSION_TYPES.map((pt) => (
                                            <option key={pt} value={pt}>{pt}</option>
                                        ))}
                                    </select>
                                    <button
                                        className="btn btn-primary btn-sm"
                                        onClick={handleGrant}
                                        disabled={actionLoading || !selectedId}
                                    >
                                        Grant
                                    </button>
                                </div>
                            </div>
                        </div>
                    </>
                )}

                <div className="modal-actions">
                    <button className="btn btn-secondary" onClick={onClose}>Close</button>
                </div>
            </div>
        </div>
    );
};

export default PermissionsModal;
