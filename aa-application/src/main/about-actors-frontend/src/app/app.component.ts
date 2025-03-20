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

  protected availableItems: string[] = [];
  protected cartItems: string[] = [];


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
            if (msgStr.startsWith('available-item-ids::')) {
              const itemsListStr: string = msgStr.split("::")[1];
              const itemsList: string[] = itemsListStr.split(',').filter(x => x);
              this.availableItems = [...itemsList];
            }
            if (msgStr.startsWith('session-item-ids::')) {
              const itemsListStr: string = msgStr.split("::")[1];
              const itemsList: string[] = itemsListStr.split(',').filter(x => x);
              this.cartItems = [...itemsList];
            }
          }
        },
        error: console.error,
        complete: () => this.wsSubject = this.getWsSubject()
      })

    return subject;
  }

  protected async handleAddItemToCart(item: string): Promise<any> {
    return await fetch('/add-item-to-cart', {
      method: "POST",
      body: JSON.stringify({sessionId: this.cookie, itemId: item}),
      headers: {
        "Content-Type": "application/json"
      }
    }).catch(console.error)
  }

  protected async handleRemoveItemFromCart(item: string): Promise<any> {
    return await fetch('/remove-item-from-cart', {
      method: "POST",
      body: JSON.stringify({sessionId: this.cookie, itemId: item}),
      headers: {
        "Content-Type": "application/json"
      }
    }).catch(console.error)
  }

  protected async handleTerminateUserSession(): Promise<any> {
    return await fetch('/terminate-user-session', {
      method: "POST",
      body: JSON.stringify({sessionId: this.cookie}),
      headers: {
        "Content-Type": "application/json"
      }
    }).catch(console.error)
  }

  protected getItemEmoji(itemId: string): string {
    switch(itemId) {
      case "001":
        return "⚾";
      case "002":
        return "⚽";
      case "003":
        return "🦀";
      case "004":
        return "🏈";
      case "005":
        return "🎷";
      case "006":
        return "⏰";
      case "007":
        return "✏️";
      default:
        return "🪅"
    }
  }
}
