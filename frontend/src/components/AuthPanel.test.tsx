import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { login, register } from '../api/client';
import { AuthPanel } from './AuthPanel';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    login: vi.fn(),
    register: vi.fn()
  };
});

const authUser = {
  listenerId: 42,
  username: 'listener42',
  emailAddress: 'listener42@example.com',
  countryName: 'United States',
  gender: 'Female',
  dateOfBirth: '1998-04-12',
  planName: 'free',
  balance: 0
};

describe('AuthPanel', () => {
  beforeEach(() => {
    vi.mocked(login).mockReset();
    vi.mocked(register).mockReset();
  });

  it('submits login credentials and returns an authenticated session', async () => {
    vi.mocked(login).mockResolvedValue(authUser);
    const onAuthenticated = vi.fn();
    const user = userEvent.setup();
    render(<AuthPanel onAuthenticated={onAuthenticated} />);

    await user.type(screen.getByLabelText('Username'), 'listener42');
    await user.type(screen.getByLabelText('Password'), 'password123');
    await user.click(loginSubmitButton());

    expect(login).toHaveBeenCalledWith({ username: 'listener42', password: 'password123' });
    expect(onAuthenticated).toHaveBeenCalledWith({
      user: authUser,
      basicAuth: `Basic ${window.btoa('listener42:password123')}`
    });
  });

  it('shows an API login error without authenticating', async () => {
    vi.mocked(login).mockRejectedValue(new Error('Invalid username or password.'));
    const onAuthenticated = vi.fn();
    const user = userEvent.setup();
    render(<AuthPanel onAuthenticated={onAuthenticated} />);

    await user.type(screen.getByLabelText('Username'), 'listener42');
    await user.type(screen.getByLabelText('Password'), 'wrongpass');
    await user.click(loginSubmitButton());

    expect(await screen.findByText('Invalid username or password.')).toBeVisible();
    expect(onAuthenticated).not.toHaveBeenCalled();
  });

  it('switches to registration and sends all registration fields', async () => {
    vi.mocked(register).mockResolvedValue(authUser);
    const onAuthenticated = vi.fn();
    const user = userEvent.setup();
    render(<AuthPanel onAuthenticated={onAuthenticated} />);

    await user.click(screen.getByRole('button', { name: 'Register' }));
    await user.type(screen.getByLabelText('Username'), 'listener42');
    await user.type(screen.getByLabelText('Email'), 'listener42@example.com');
    await user.type(screen.getByLabelText('Gender'), 'Female');
    fireEvent.change(screen.getByLabelText('Date of birth'), { target: { value: '1998-04-12' } });
    await user.clear(screen.getByLabelText('Country'));
    await user.type(screen.getByLabelText('Country'), 'United States');
    await user.type(screen.getByLabelText('Password'), 'password123');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(register).toHaveBeenCalledWith({
      username: 'listener42',
      emailAddress: 'listener42@example.com',
      password: 'password123',
      gender: 'Female',
      dateOfBirth: '1998-04-12',
      countryName: 'United States'
    });
    expect(onAuthenticated).toHaveBeenCalledOnce();
  });
});

function loginSubmitButton(): HTMLButtonElement {
  const button = screen.getAllByRole('button', { name: 'Login' })
    .find((candidate) => candidate.getAttribute('type') === 'submit');

  if (!(button instanceof HTMLButtonElement)) {
    throw new Error('Login submit button was not found.');
  }
  return button;
}
