import {provideHttpClient} from "@angular/common/http";
import {ApplicationConfig, isDevMode} from "@angular/core";
import {provideRouter} from "@angular/router";

import {providePrimeNG} from "primeng/config";
import {provideTransloco} from "@jsverse/transloco";

import {myPreset} from "../theme-preset";
import {TranslocoHttpLoader} from "../transloco-loader";
import {routes} from "./app.routes";
import {getCookie} from "./utility/utilities";

export const appConfig: ApplicationConfig = {
	providers: [
		provideHttpClient(),
		provideRouter(routes),
		providePrimeNG({
			theme: {
				preset: myPreset,
				options: {darkModeSelector: ".dark-theme"},
			},
		}),
		provideTransloco({
			config: {
				availableLangs: ["en", "zh"],
				defaultLang: getCookie("language") || "en",
				reRenderOnLangChange: true,
				prodMode: !isDevMode(),
			},
			loader: TranslocoHttpLoader,
		}),
	],
};
