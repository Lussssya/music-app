import { FormEvent, useState } from 'react';
import { LogIn, UserPlus } from 'lucide-react';
import { login, register } from '../api/client';
import type { AuthUser, LoginRequest, RegisterRequest } from '../types';

type AuthMode = 'login' | 'register';

type AuthPanelProps = {
  onAuthenticated: (user: AuthUser) => void;
};

const initialLogin: LoginRequest = {
  username: '',
  password: ''
};

const initialRegister: RegisterRequest = {
  username: '',
  emailAddress: '',
  password: '',
  gender: '',
  dateOfBirth: '',
  countryName: 'United States'
};

export function AuthPanel({ onAuthenticated }: AuthPanelProps) {
  const [mode, setMode] = useState<AuthMode>('login');
  const [loginForm, setLoginForm] = useState<LoginRequest>(initialLogin);
  const [registerForm, setRegisterForm] = useState<RegisterRequest>(initialRegister);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const isLogin = mode === 'login';

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const user = isLogin ? await login(loginForm) : await register(registerForm);
      onAuthenticated(user);
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : 'Something went wrong.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="auth-layout">
      <div className="auth-copy">
        <p className="eyebrow">Music App</p>
        <h1>Back-office shell for testing the music platform.</h1>
        <p>Sign in with an existing seeded listener or register a new one, then move through catalog, playlists, listener actions, and recommendations from one place.</p>
      </div>

      <form className="auth-form" onSubmit={submit}>
        <div className="segmented-control" aria-label="Authentication mode">
          <button type="button" className={isLogin ? 'active' : ''} onClick={() => setMode('login')}>
            <LogIn size={16} />
            Login
          </button>
          <button type="button" className={!isLogin ? 'active' : ''} onClick={() => setMode('register')}>
            <UserPlus size={16} />
            Register
          </button>
        </div>

        <label>
          Username
          <input
            value={isLogin ? loginForm.username : registerForm.username}
            onChange={(event) => isLogin
              ? setLoginForm({ ...loginForm, username: event.target.value })
              : setRegisterForm({ ...registerForm, username: event.target.value })}
            autoComplete="username"
            required
          />
        </label>

        {!isLogin && (
          <>
            <label>
              Email
              <input
                type="email"
                value={registerForm.emailAddress}
                onChange={(event) => setRegisterForm({ ...registerForm, emailAddress: event.target.value })}
                autoComplete="email"
                required
              />
            </label>

            <div className="form-grid">
              <label>
                Gender
                <input
                  value={registerForm.gender}
                  onChange={(event) => setRegisterForm({ ...registerForm, gender: event.target.value })}
                  required
                />
              </label>
              <label>
                Date of birth
                <input
                  type="date"
                  value={registerForm.dateOfBirth}
                  onChange={(event) => setRegisterForm({ ...registerForm, dateOfBirth: event.target.value })}
                  required
                />
              </label>
            </div>

            <label>
              Country
              <input
                value={registerForm.countryName}
                onChange={(event) => setRegisterForm({ ...registerForm, countryName: event.target.value })}
                required
              />
            </label>
          </>
        )}

        <label>
          Password
          <input
            type="password"
            value={isLogin ? loginForm.password : registerForm.password}
            onChange={(event) => isLogin
              ? setLoginForm({ ...loginForm, password: event.target.value })
              : setRegisterForm({ ...registerForm, password: event.target.value })}
            autoComplete={isLogin ? 'current-password' : 'new-password'}
            required
            minLength={8}
          />
        </label>

        {error && <p className="form-error">{error}</p>}

        <button className="primary-action" type="submit" disabled={loading}>
          {isLogin ? <LogIn size={18} /> : <UserPlus size={18} />}
          {loading ? 'Please wait' : isLogin ? 'Login' : 'Create account'}
        </button>
      </form>
    </section>
  );
}
