import {Routes} from "@angular/router";

import {HomeComponent} from "./component/home/home.component";
import {RoomComponent} from "./component/room/room.component";

export const routes: Routes = [
	{path: "", component: HomeComponent},
	{path: "room/:code", component: RoomComponent},
	{path: "**", redirectTo: ""},
];
