import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AiParticipant } from '../../dto/AiRoomStatus';

@Component({
  selector: 'app-agent-picker',
  imports: [CommonModule],
  template: `
    <fieldset [disabled]="disabled">
      <legend>{{ legend }}</legend>
      <label *ngFor="let agent of agents">
        <input type="checkbox" [checked]="selectedKeys.includes(agent.key)"
          (change)="select(agent.key, $any($event.target).checked)">
        <span><strong>{{ agent.displayName }}</strong><small>{{ agent.description }}</small></span>
      </label>
    </fieldset>
  `,
  styles: `
    :host { display: block; margin: 14px 0; }
    fieldset { border: 0; padding: 0; margin: 0; }
    legend { font-weight: 600; color: #334155; margin-bottom: 8px; }
    label { display: flex; gap: 10px; align-items: flex-start; padding: 9px 10px;
      margin-bottom: 6px; border: 1px solid #c7d2fe; border-radius: 8px; background: white; cursor: pointer; }
    input { margin: 3px 0 0; accent-color: #4f46e5; width: 16px; height: 16px; flex-shrink: 0; }
    input:focus-visible { outline: 3px solid #a5b4fc; outline-offset: 2px; }
    strong { font-size: 14px; color: #312e81; }
    small { display: block; color: #475569; font-size: 12px; line-height: 1.4; margin-top: 2px; }
    fieldset:disabled label { cursor: not-allowed; opacity: .65; }
  `
})
export class AgentPickerComponent {
  @Input() agents: AiParticipant[] = [];
  @Input() selectedKeys: string[] = [];
  @Input() disabled = false;
  @Input() legend = 'Invita agenti IA';
  @Output() selectedKeysChange = new EventEmitter<string[]>();

  select(key: string, checked: boolean): void {
    if (this.disabled) return;
    const selection = new Set(this.selectedKeys);
    checked ? selection.add(key) : selection.delete(key);
    this.selectedKeysChange.emit(this.agents.filter(agent => selection.has(agent.key)).map(agent => agent.key));
  }
}
