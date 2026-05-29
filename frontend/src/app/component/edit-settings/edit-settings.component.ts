import {ChangeDetectionStrategy, Component, effect, inject, input, model} from "@angular/core";
import {FormControl, FormGroup, FormsModule, ReactiveFormsModule} from "@angular/forms";

import {TranslocoDirective} from "@jsverse/transloco";
import {ButtonModule} from "primeng/button";
import {DialogModule} from "primeng/dialog";
import {DividerModule} from "primeng/divider";
import {FloatLabelModule} from "primeng/floatlabel";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {InputTextModule} from "primeng/inputtext";
import {ScrollerModule} from "primeng/scroller";
import {Slider} from "primeng/slider";
import {ToggleSwitch} from "primeng/toggleswitch";

import {RoomView} from "../../model/atrium-event";
import {RequestService} from "../../service/request.service";
import {ButtonWithLoadingComponent} from "../button-with-loading/button-with-loading.component";

interface RoomSettingsFormControls {
	name: FormControl<string>;
	players: FormControl<[number, number]>;
	isPrivate: FormControl<boolean>;
}

@Component({
	selector: "app-edit-settings",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		ButtonModule,
		ButtonWithLoadingComponent,
		DialogModule,
		DividerModule,
		FloatLabelModule,
		FormsModule,
		IconFieldModule,
		InputIconModule,
		InputTextModule,
		ReactiveFormsModule,
		ScrollerModule,
		Slider,
		ToggleSwitch,
		TranslocoDirective,
	],
	templateUrl: "./edit-settings.component.html",
	styleUrl: "./edit-settings.component.scss",
})
export class EditSettingsComponent {
	private readonly requestService = inject(RequestService);

	dialogVisible = model.required<boolean>();
	room = input.required<RoomView>();
	protected readonly formGroup: FormGroup<RoomSettingsFormControls>;

	constructor() {
		this.formGroup = new FormGroup({
			name: new FormControl("", {nonNullable: true}),
			players: new FormControl<[number, number]>([1, 1], {nonNullable: true}),
			isPrivate: new FormControl(true, {nonNullable: true}),
		});

		effect(() => {
			const room = this.room();
			this.formGroup.setValue({
				name: room.name ?? "",
				players: [room.minPlayers, room.maxPlayers],
				isPrivate: room.isPrivate,
			});
		});
	}

	save() {
		const formValues = this.formGroup.getRawValue();
		return this.requestService.updateSettings(this.room().code, formValues.name, formValues.players[0], formValues.players[1], formValues.isPrivate);
	}

	getMinPlayers() {
		const formValues = this.formGroup.getRawValue();
		return Math.min(formValues.players[0], formValues.players[1]);
	}

	getMaxPlayers() {
		const formValues = this.formGroup.getRawValue();
		return Math.max(formValues.players[0], formValues.players[1]);
	}
}
