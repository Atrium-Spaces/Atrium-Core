import {ChangeDetectionStrategy, Component, model} from "@angular/core";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";

import {TranslocoDirective} from "@jsverse/transloco";
import {AvatarModule} from "primeng/avatar";
import {ButtonModule} from "primeng/button";
import {FloatLabelModule} from "primeng/floatlabel";
import {InputTextModule} from "primeng/inputtext";
import {TooltipModule} from "primeng/tooltip";

import {EditProfileComponent} from "../edit-profile/edit-profile.component";
import {HeaderComponent} from "../header/header.component";
import {ProfileComponent} from "../profile/profile.component";

@Component({
	selector: "app-home",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		AvatarModule,
		FloatLabelModule,
		InputTextModule,
		ButtonModule,
		TooltipModule,
		TranslocoDirective,
		ReactiveFormsModule,
		HeaderComponent,
		FormsModule,
		EditProfileComponent,
		ProfileComponent,
	],
	templateUrl: "./home.component.html",
	styleUrl: "./home.component.scss",
})
export class HomeComponent {
	protected dialogVisible = model(false);
	protected readonly roomCodeLength = 6;

	updateRoomCode(roomCodeInput: HTMLInputElement) {
		const value = roomCodeInput.value;
		const newValue = value.toUpperCase().replaceAll(/[^A-Z\d]/g, "");
		if (newValue !== value) {
			roomCodeInput.value = newValue;
		}
	}

	joinRoom(event: SubmitEvent, roomCode: string) {
		event.stopPropagation();
		console.log(roomCode);
		// TODO
	}
}
