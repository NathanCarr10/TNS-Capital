import { SetMetadata } from '@nestjs/common';
import type { Role } from '../../users/users.service';

export const ROLES_KEY = 'roles';

/** Limits a route to users holding at least one of these roles (checked by RolesGuard). */
export const Roles = (...roles: Role[]) => SetMetadata(ROLES_KEY, roles);
