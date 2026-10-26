import React, { createContext, useContext, useState, useEffect } from 'react';
import { UserPrincipal, Role } from '../types';
import { authApi } from '../services/authApi';

interface AuthContextType {
  user: UserPrincipal | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (roles: Role | Role[]) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserPrincipal | null>(() => {
    const saved = localStorage.getItem('helixflow_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('helixflow_token'));
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const handleAuthExpired = () => {
      setUser(null);
      setToken(null);
    };

    window.addEventListener('helixflow:auth_expired', handleAuthExpired);
    return () => window.removeEventListener('helixflow:auth_expired', handleAuthExpired);
  }, []);

  useEffect(() => {
    async function verifyAuth() {
      if (token) {
        try {
          const current = await authApi.getCurrentUser();
          setUser(current);
          localStorage.setItem('helixflow_user', JSON.stringify(current));
        } catch (err) {
          console.warn('Session verification failed, logging out', err);
          setUser(null);
          setToken(null);
          localStorage.removeItem('helixflow_token');
          localStorage.removeItem('helixflow_user');
        }
      }
      setIsLoading(false);
    }

    verifyAuth();
  }, [token]);

  const login = async (username: string, password: string) => {
    setIsLoading(true);
    try {
      const resp = await authApi.login({ username, password });
      const principal: UserPrincipal = {
        userId: resp.userId,
        username: resp.username,
        fullName: resp.fullName,
        role: resp.role,
      };

      setToken(resp.token);
      setUser(principal);
      localStorage.setItem('helixflow_token', resp.token);
      localStorage.setItem('helixflow_user', JSON.stringify(principal));
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    try {
      if (token) {
        await authApi.logout();
      }
    } catch (err) {
      console.warn('Logout API call failed', err);
    } finally {
      setUser(null);
      setToken(null);
      localStorage.removeItem('helixflow_token');
      localStorage.removeItem('helixflow_user');
    }
  };

  const hasRole = (roles: Role | Role[]): boolean => {
    if (!user) return false;
    if (user.role === 'ADMIN') return true; // ADMIN has super-access to inspect all modules
    const roleList = Array.isArray(roles) ? roles : [roles];
    return roleList.includes(user.role);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token && !!user,
        isLoading,
        login,
        logout,
        hasRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
