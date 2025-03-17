import {Component} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {WebSocketSubject} from 'rxjs/internal/observable/dom/WebSocketSubject';
import {webSocket} from 'rxjs/webSocket';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.sass'
})
export class AppComponent {
  title = 'about-actors-frontend';
  private wsSubject: WebSocketSubject<any> = this.getWsSubject();
  private cookie: string = "";

  protected log: string[] = [];

  protected sendWebsocketMessage(msg: string) {
    this.wsSubject.next(`${this.cookie}::${msg}`);
  }

  protected handleFailToStartUserSessionClick() {
    this.sendWebsocketMessage("fail-user-session");
  }

  private getWsSubject(): WebSocketSubject<any> {
    const subject = webSocket({
      url: 'ws://localhost:4201/aa-websocket',
    });

    subject
      .subscribe({
        next: (msg: unknown) => {
          if (typeof msg === 'string') {
            this.log.push(`[${new Date().toISOString()}] :: WS Message :: ${msg}`);
            const msgStr: string = msg as string;
            if (msgStr.startsWith('cookie::')) {
              this.cookie = msgStr.split('::')[1];
              this.sendWebsocketMessage("valid");
            }
          }
        },
        error: console.error,
        complete: () => this.wsSubject = this.getWsSubject()
      })

    return subject;
  }
}
