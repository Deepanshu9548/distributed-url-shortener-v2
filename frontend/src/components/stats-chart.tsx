import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';

export function StatsChart({ stats }: { stats: { clickCount: number; lastClickAt: string | null } }) {
  // Recharts needs an array of data. Since we only have total clickCount, we'll display a simple single bar.
  // In a real app we'd have timeseries data.
  const data = [
    {
      name: 'Total Clicks',
      clicks: stats.clickCount,
    }
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Link Analytics</CardTitle>
        <CardDescription>
          {stats.lastClickAt ? `Last clicked: ${new Intl.DateTimeFormat('default', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(stats.lastClickAt))}` : 'No clicks yet'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="h-[200px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
              <XAxis dataKey="name" />
              <YAxis allowDecimals={false} />
              <Tooltip cursor={{ fill: 'transparent' }} />
              <Bar dataKey="clicks" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} maxBarSize={60} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  );
}
