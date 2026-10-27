import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-label="Settings">
      <div class="settings-grid">
        <button class="machine-button" disabled>MANAGEMENT</button>
        <button class="machine-button" disabled>MAINTENANCE</button>
        <button class="machine-button" disabled>CIT</button>
      </div>

      <p class="availability-note">
        Role login will be available when authentication is implemented.
      </p>
    </section>
  `,
  styles: `
    .settings-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }

    .machine-button {
      min-height: 110px;
      padding: 16px;
      font-size: clamp(16px, 2.5vw, 24px);
    }

    .availability-note {
      margin-top: 28px;
      color: var(--muted);
      text-align: center;
      font-size: 15px;
    }
  `,
})
export class SettingsPage {}
