import {ChangeDetectionStrategy, Component, inject} from "@angular/core";
import {RouterOutlet} from "@angular/router";

import {RequestService} from "./service/request.service";

@Component({
	selector: "app-root",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		RouterOutlet,
	],
	templateUrl: "./app.component.html",
	styleUrl: "./app.component.scss",
})
export class AppComponent {
	private readonly requestService = inject(RequestService);

	constructor() {
		this.requestService.init();
	}
}
