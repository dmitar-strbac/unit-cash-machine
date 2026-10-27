import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-settings-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-label="Settings">
      <div class="settings-grid">
        <a class="machine-button" routerLink="/login/management">MANAGEMENT</a>
        <a class="machine-button" routerLink="/login/maintenance">MAINTENANCE</a>
        <a class="machine-button" routerLink="/login/cit">CIT</a>
      </div>
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
  `,
})
export class SettingsPage {}
