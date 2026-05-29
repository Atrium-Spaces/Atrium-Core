import {effect, Injectable, signal} from "@angular/core";
import {getCookie, setCookie} from "../utility/utilities";

@Injectable({providedIn: "root"})
export class AuthenticationService {
	readonly publicId = signal<string>(getCookie("public_id"));
	readonly secretId = signal<string>(getCookie("secret_id"));
	readonly name = signal<string>("");
	readonly avatar = signal<string>("");

	constructor() {
		effect(() => AuthenticationService.setCookieIfValid("public_id", this.publicId()));
		effect(() => AuthenticationService.setCookieIfValid("secret_id", this.secretId()));
	}

	private static setCookieIfValid(name: string, value: string) {
		if (value) {
			setCookie(name, value);
		}
	}
}
