import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="home-grid" aria-label="Main menu">
      <button class="machine-button home-tile" disabled>
        <svg viewBox="0 0 100 80" aria-hidden="true">
          <circle cx="50" cy="22" r="16" />
          <circle cx="17" cy="29" r="10" />
          <circle cx="83" cy="29" r="10" />
          <path d="M25 73V60a25 25 0 0 1 50 0v13Z" />
          <path d="M2 62V51a15 15 0 0 1 28-7c-6 6-9 12-9 18Z" />
          <path d="M79 62c0-6-3-12-9-18a15 15 0 0 1 28 7v11Z" />
        </svg>
        <span>DEPOSIT</span>
        <small>Login will be available in a later update</small>
      </button>

      <a class="machine-button home-tile" routerLink="/settings">
        <svg viewBox="0 0 100 100" aria-hidden="true">
          <defs>
            <mask id="settings-gear-hole">
              <rect width="100" height="100" fill="white" />
              <circle cx="50" cy="50" r="17" fill="black" />
            </mask>
          </defs>

          <g fill="currentColor" mask="url(#settings-gear-hole)">
            <circle cx="50" cy="50" r="34" />

            <rect x="41" y="5" width="18" height="24" rx="3" />
            <rect x="41" y="5" width="18" height="24" rx="3" transform="rotate(60 50 50)" />
            <rect x="41" y="5" width="18" height="24" rx="3" transform="rotate(120 50 50)" />
            <rect x="41" y="5" width="18" height="24" rx="3" transform="rotate(180 50 50)" />
            <rect x="41" y="5" width="18" height="24" rx="3" transform="rotate(240 50 50)" />
            <rect x="41" y="5" width="18" height="24" rx="3" transform="rotate(300 50 50)" />
          </g>
        </svg>
        <span>SETTINGS</span>
      </a>
    </section>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }

    .home-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
      height: 100%;
    }

    .home-tile {
      flex-direction: column;
      gap: 28px;
      padding: 24px;
      font-size: clamp(24px, 4vw, 42px);
      text-align: center;
    }

    svg {
      width: clamp(80px, 14vw, 150px);
      height: 130px;
      fill: currentColor;
    }

    small {
      max-width: 240px;
      font-size: 14px;
      font-weight: normal;
      text-transform: none;
    }
  `,
})
export class HomePage {}
