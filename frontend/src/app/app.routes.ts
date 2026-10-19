import { Routes } from '@angular/router';

import { authGuard, notAdminGuard, supportGuard } from './core/auth/auth.guard';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login/login.component';
import { RegisterComponent } from './features/register/register.component';
import { ShellComponent } from './layout/shell/shell.component';

// The signed-in pages load on first visit, keeping the sign-in pages light
const loadTrade = () => import('./features/trade/trade.component').then((m) => m.TradeComponent);

export const routes: Routes = [
	{ path: 'login', component: LoginComponent, title: 'Log in · TNS Capital' },
	{ path: 'register', component: RegisterComponent, title: 'Register · TNS Capital' },
	{ path: '', pathMatch: 'full', component: HomeComponent, title: 'TNS Capital' },
	{
		path: '',
		component: ShellComponent,
		canActivate: [authGuard, notAdminGuard],
		children: [
			{ path: 'portfolio', loadComponent: () => import('./features/portfolio/portfolio.component').then((m) => m.PortfolioComponent), title: 'Portfolio · TNS Capital' },
			// Without a symbol, the trade page opens the last symbol viewed or the first one available
			{ path: 'trade', loadComponent: loadTrade, title: 'Trade · TNS Capital' },
			{ path: 'trade/:symbol', loadComponent: loadTrade, title: 'Trade · TNS Capital' },
			{ path: 'orders', loadComponent: () => import('./features/orders/order-history.component').then((m) => m.OrderHistoryComponent), title: 'Order History · TNS Capital' },
			{ path: 'strategies', loadComponent: () => import('./features/strategy-lab/strategy-lab.component').then((m) => m.StrategyLabComponent), title: 'Strategy Lab · TNS Capital' },
			{ path: 'dead-letters', canActivate: [supportGuard], loadComponent: () => import('./features/dead-letters/dead-letters.component').then((m) => m.DeadLettersComponent), title: 'Dead Letters · TNS Capital' },
			{ path: 'settings', loadComponent: () => import('./features/settings/settings.component').then((m) => m.SettingsComponent), title: 'Settings · TNS Capital' },
			// Old links
			{ path: 'dashboard', redirectTo: 'portfolio' },
			{ path: 'symbols/:symbol', redirectTo: 'trade/:symbol' },
		],
	},
	{ path: '**', redirectTo: '' },
];
