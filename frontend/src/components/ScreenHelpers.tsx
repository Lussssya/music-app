import type { ReactNode } from 'react';

type StatusMessageProps = {
  error: string | null;
  message: string | null;
};

export function StatusMessage({ error, message }: StatusMessageProps) {
  if (!error && !message) {
    return null;
  }

  return <p className={error ? 'form-error' : 'form-success'}>{error ?? message}</p>;
}

type EmptyStateProps = {
  children: ReactNode;
};

export function EmptyState({ children }: EmptyStateProps) {
  return <p className="empty-state">{children}</p>;
}

export function displayError(caughtError: unknown): string {
  return caughtError instanceof Error ? caughtError.message : 'Something went wrong.';
}
