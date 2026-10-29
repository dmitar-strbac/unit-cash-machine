import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-maintenance-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @switch (name()) {
        @case ('reboot') {
          <path d="M19.5 7a8 8 0 1 0 .5 9" stroke-width="2.5" />
          <path d="M21.5 2.5v7h-7Z" fill="currentColor" stroke="none" />
        }

        @case ('reset') {
          @for (angle of [0, 120, 240]; track angle) {
            <g [attr.transform]="'rotate(' + angle + ' 12 14.5)'">
              <path
                d="M8.5 10.56 10.75 6.67Q12 4.5 13.25 6.67L14.25 8.4"
                stroke-width="2.2"
              />
              <path
                d="M15.75 11 15.98 7.4 12.52 9.4Z"
                fill="currentColor"
                stroke-width="1"
              />
            </g>
          }
        }

        @default {
          <path [attr.d]="paths[name()] ?? paths['components']" />
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      width: 52px;
      height: 52px;
      flex-shrink: 0;
    }

    svg {
      width: 100%;
      height: 100%;
    }
  `,
})
export class MaintenanceIcon {
  readonly name = input.required<string>();

  protected readonly paths: Partial<Record<string, string>> = {
    power: 'M12 2v10 M5.6 5.6a9 9 0 1 0 12.8 0',

    exit: 'M10 3H3v18h7 M9 12h13 M17 7l5 5-5 5',

    system: 'M3 3h18v13H3z M12 16v5 M7 21h10',

    components: 'M12 2l9 5-9 5-9-5z M3 7v10l9 5 9-5V7 M12 12v10',

    acceptor:
      'M3 3h18v18H3z M6 7h12 M7 11h10v7H7z M10 14h4',

    cassette:
      'M4 7h16v14H4z M8 7V3h8v4 M4 11h16 M10 15h4',

    door:
      'M4 21h16 M6 21V3h12v18 M14 12h.01',

    lock:
      'M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4 M12 14v3',

    board:
      'M6 6h12v12H6z M9 9h6v6H9z M9 2v4 M15 2v4 M9 18v4 M15 18v4 M2 9h4 M2 15h4 M18 9h4 M18 15h4',

    printer:
      'M6 8V3h12v5 M6 17H3V8h18v9h-3 M6 14h12v7H6z M17 11h1',

    doorClosed:
      'M3 21h11 M5 21V3h12v8 M11 12h.01 M15 17l2 2 4-5',

    lockSecure:
      'M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4 M9 15l2 2 4-4',

    shield:
      'M12 2l8 3v6c0 5-3.5 8.5-8 11-4.5-2.5-8-6-8-11V5z M8 12l3 3 5-6',
  };
}
