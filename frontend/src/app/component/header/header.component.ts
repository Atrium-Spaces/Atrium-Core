import {ChangeDetectionStrategy, Component, DestroyRef, inject} from "@angular/core";
import {takeUntilDestroyed} from "@angular/core/rxjs-interop";

import {TranslocoDirective, TranslocoService} from "@jsverse/transloco";
import {MenuItem, PrimeIcons} from "primeng/api";
import {ButtonModule} from "primeng/button";
import {MenuModule} from "primeng/menu";
import {TooltipModule} from "primeng/tooltip";

import {ThemeService} from "../../service/theme.service";
import {getLanguageMapping, setCookie} from "../../utility/utilities";

@Component({
	selector: "app-header",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		ButtonModule,
		MenuModule,
		TooltipModule,
		TranslocoDirective,
	],
	templateUrl: "./header.component.html",
	styleUrl: "./header.component.scss",
})
export class HeaderComponent {
	private readonly destroyRef = inject(DestroyRef);
	private readonly themeService = inject(ThemeService);
	private readonly translocoService = inject(TranslocoService);

	readonly languageMenuItems: MenuItem[] = this.translocoService.getAvailableLangs().map(langDefinition => {
		const lang = langDefinition.toString();
		return ({
			id: lang,
			icon: PrimeIcons.CHECK,
			label: getLanguageMapping(lang),
			command: () => {
				this.translocoService.setActiveLang(lang);
				setCookie("language", lang);
			},
		});
	});

	constructor() {
		this.translocoService.events$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(event => {
			if (event.type === "translationLoadSuccess" || event.type === "langChanged") {
				this.updateMenuItems();
			}
		});

		this.updateMenuItems();
	}

	toggleTheme() {
		this.themeService.setTheme(!this.themeService.darkTheme());
	}

	getThemeIcon() {
		return this.themeService.darkTheme() ? PrimeIcons.SUN : PrimeIcons.MOON;
	}

	getThemeTranslationKey() {
		return this.themeService.darkTheme() ? "header.lightTheme" : "header.darkTheme";
	}

	private updateMenuItems() {
		this.languageMenuItems.forEach(menuItem => menuItem.iconStyle = this.translocoService.getActiveLang() === menuItem.id ? {} : {color: "transparent"});
	}
}
