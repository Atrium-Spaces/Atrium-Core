import {ChangeDetectionStrategy, Component, input, output, signal} from "@angular/core";
import {ButtonModule, ButtonSeverity} from "primeng/button";
import {Observable} from "rxjs";

@Component({
	selector: "app-button-with-loading",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		ButtonModule,
	],
	templateUrl: "./button-with-loading.component.html",
	styleUrl: "./button-with-loading.component.scss",
})
export class ButtonWithLoadingComponent<T> {
	readonly label = input.required<string>();
	readonly icon = input.required<string>();
	readonly text = input(true);
	readonly clickAction = input.required<Observable<T>>();
	readonly disabled = input(false);
	readonly severity = input<ButtonSeverity>(undefined);
	readonly clickSuccess = output<T>();
	readonly clickError = output();
	protected readonly loading = signal(false);

	click() {
		this.loading.set(true);
		this.clickAction().subscribe({
			next: data => {
				this.clickSuccess.emit(data);
				this.loading.set(false);
			},
			error: () => {
				this.clickError.emit();
				this.loading.set(false);
			},
		});
	}
}
