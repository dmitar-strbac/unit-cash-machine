import { HttpErrorResponse } from '@angular/common/http';

export function authErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'The request could not be completed. Please try again.';
  }

  switch (error.status) {
    case 0:
      return 'Cannot connect to the machine. Please try again.';
    case 400:
      return 'Enter a PIN containing 4 to 12 digits.';
    case 401:
      return 'Invalid PIN or account unavailable.';
    case 403:
      return 'The security check failed. Please try again.';
    case 409:
      return 'A user is already signed in.';
    case 429:
      return 'Too many login attempts. Please wait one minute and try again.';
    default:
      return 'The request could not be completed. Please try again.';
  }
}
