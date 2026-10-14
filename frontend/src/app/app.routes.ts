import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login/login.component';
import { SymbolDetailsComponent } from './features/symbol-details/symbol-details.component';
import { RegisterComponent } from './features/register/register.component';

export const routes: Routes = [
	{ path: 'login', component: LoginComponent },
	{ path: 'register', component: RegisterComponent },
	{ path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
	{ path: 'symbols/:symbol', component: SymbolDetailsComponent, canActivate: [authGuard] },
	{ path: '', pathMatch: 'full', component: HomeComponent },
	{ path: '**', redirectTo: '' },
];
