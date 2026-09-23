import { useState } from 'react';
import { activatePassword } from '../auth/session';
import { adminEnvironment } from '../config/environment';
import { translateText, useI18n } from '../i18n';
import { Field } from './admin-ui';

export const AccountActivation = () => {
    const { locale } = useI18n();
    const copy = (source: string) => translateText(locale, source);
    const token = new URLSearchParams(window.location.search).get('token') ?? '';
    const [password, setPassword] = useState('');
    const [confirmation, setConfirmation] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [complete, setComplete] = useState(false);

    const submit = async (event: React.FormEvent) => {
        event.preventDefault();
        setError('');
        if (!token) {
            setError(copy('This activation link is missing its token.'));
            return;
        }
        if (password !== confirmation) {
            setError(copy('The passwords do not match.'));
            return;
        }
        setBusy(true);
        try {
            await activatePassword(adminEnvironment, token, password);
            setComplete(true);
        } catch {
            setError(copy('This activation link is invalid or expired. Request a new invitation from an administrator.'));
        } finally {
            setBusy(false);
        }
    };

    return <main className="admin-shell" aria-labelledby="account-activation-title">
        <section className="admin-shell__panel">
            <h1 id="account-activation-title">{copy('Activate your Cadentia account')}</h1>
            {complete ? <>
                <p role="status">{copy('Your account is ready. Sign in with your email and new password.')}</p>
                <a className="admin-shell__button" href="/admin">{copy('Go to sign in')}</a>
            </> : <form className="admin-auth-form" onSubmit={(event) => void submit(event)}>
                <p>{copy('Set a password to finish activating your account.')}</p>
                {error && <p role="alert" className="admin-shell__warning">{error}</p>}
                <Field label="New password" required description="Use at least 12 characters.">{({ inputId }) => <input id={inputId} type="password" autoComplete="new-password" minLength={12} required value={password} onChange={(event) => setPassword(event.target.value)} />}</Field>
                <Field label="Confirm password" required>{({ inputId }) => <input id={inputId} type="password" autoComplete="new-password" minLength={12} required value={confirmation} onChange={(event) => setConfirmation(event.target.value)} />}</Field>
                <button type="submit" disabled={busy || !token}>{copy(busy ? 'Activating account…' : 'Activate account')}</button>
            </form>}
        </section>
    </main>;
};
