import { Link } from 'react-router-dom';
import { useAuthStore } from '@/lib/auth-store';
import { useLogout } from '@/hooks/use-auth';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { User, Link as LinkIcon, LogOut } from 'lucide-react';

export function NavBar() {
  const { isAuthenticated } = useAuthStore();
  const logoutMutation = useLogout();
  const isAuth = isAuthenticated();

  return (
    <nav className="border-b bg-background">
      <div className="container flex h-16 items-center justify-between px-4 sm:px-8 max-w-6xl mx-auto">
        <Link to="/" className="flex items-center gap-2 font-bold text-lg text-primary">
          <LinkIcon className="h-6 w-6" />
          <span>Shortr</span>
        </Link>
        <div className="flex items-center gap-4">
          {isAuth ? (
            <>
              <Button variant="ghost" asChild>
                <Link to="/dashboard">Dashboard</Link>
              </Button>
              <Button variant="default" asChild>
                <Link to="/links/new">New Link</Link>
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" size="icon" className="rounded-full">
                    <User className="h-4 w-4" />
                    <span className="sr-only">Toggle user menu</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onClick={() => logoutMutation.mutate()} className="text-destructive cursor-pointer">
                    <LogOut className="mr-2 h-4 w-4" />
                    <span>Log out</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </>
          ) : (
            <>
              <Button variant="ghost" asChild>
                <Link to="/login">Log in</Link>
              </Button>
              <Button variant="default" asChild>
                <Link to="/register">Sign up</Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </nav>
  );
}
