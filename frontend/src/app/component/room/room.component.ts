import {ChangeDetectionStrategy, Component} from "@angular/core";

import {ButtonModule} from "primeng/button";
import {TooltipModule} from "primeng/tooltip";

@Component({
	selector: "app-room",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		ButtonModule,
		TooltipModule,
	],
	templateUrl: "./room.component.html",
	styleUrl: "./room.component.scss",
})
export class RoomComponent {

}
