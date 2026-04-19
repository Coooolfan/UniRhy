import type {AccountPreferences} from './';

export interface AccountUpdate {
    readonly name?: string | undefined;
    readonly password?: string | undefined;
    readonly email?: string | undefined;
    readonly preferences?: AccountPreferences | undefined;
}
