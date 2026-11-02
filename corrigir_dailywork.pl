use strict;
use warnings;

my $file = "app/src/main/assets/dailywork-alunos.html";
open my $in, "<", $file or die "Não encontrei o HTML: $!";
local $/;
my $html = <$in>;
close $in;

my $backup = "$file.backup";
open my $bk, ">", $backup or die "Erro no backup: $!";
print $bk $html;
close $bk;

my %found;

my @lines = split /\n/, $html, -1;
for my $i (0 .. $#lines) {
    if ($lines[$i] =~ /^function updateSettings\(\)/) {
        $lines[$i] = "function updateSettings(){settings.glass=$('#glassToggle').checked;settings.intensity=Number($('#intensity').value);settings.effect=$('#effect').value;settings.notify=$('#notifyToggle').checked;settings.reminders=reminders;settings.sound=$('#soundToggle').checked;settings.vibrate=$('#vibrateToggle').checked;persist();syncNativeReminders();$('#intensityValue').textContent=settings.intensity+'%';applyGlass();syncRealBar();renderOverview();}";
        $found{update}++;
    }

    if ($lines[$i] =~ /^function saveReminders\(\)/) {
        $lines[$i] = "function saveReminders(){settings.reminders=reminders;persist();syncNativeReminders();syncRealBar();renderOverview();}";
        $found{save}++;
    }

    if ($lines[$i] =~ /^function fireReminder\(/) {
        $lines[$i] = "function fireReminder(test=false,reminderName='Lembrete'){const pill=$('#island');pill.classList.remove('shake','reminder-wiggle');void pill.offsetWidth;pill.classList.add('reminder-wiggle');setTimeout(()=>pill.classList.remove('reminder-wiggle'),2300);if(settings.sound)playSound();if(settings.vibrate){if(window.DailyWorkNative&&DailyWorkNative.vibrate){DailyWorkNative.vibrate();}else if(navigator.vibrate){navigator.vibrate([220,80,220,80,320]);}}if(!test)toast('Hora do '+reminderName+'!');}";
        $found{fire}++;
    }

    if ($lines[$i] =~ /^\s*\$\('#notifyToggle'\)\.addEventListener\('change'/) {
        $lines[$i] = q{$('#notifyToggle').addEventListener('change',()=>{if($('#notifyToggle').checked){if(window.DailyWorkNative&&DailyWorkNative.requestNotificationPermission){DailyWorkNative.requestNotificationPermission();$('#notificationStatus').textContent='Solicitação de permissão enviada ao Android.';}else{$('#notificationStatus').textContent='Abra esta função pelo aplicativo Android.';}}updateSettings();syncNativeReminders();});};
        $found{notify}++;
    }
}

for my $key (qw(update save fire notify)) {
    die "Não encontrei o trecho: $key. HTML original preservado.\n" unless $found{$key};
}

$html = join "\n", @lines;

my $native = <<'JS';
function syncNativeReminders(){
    if(!window.DailyWorkNative)return;
    const active=settings.notify?reminders.filter(r=>r.enabled):[];
    if(DailyWorkNative.scheduleReminders){
        DailyWorkNative.scheduleReminders(
            JSON.stringify(active),
            !!settings.vibrate,
            !!settings.sound
        );
    }
}
JS

die "A função nativa já existe. Não apliquei outra cópia.\n"
    if $html =~ /function syncNativeReminders\s*\(/;

$html =~ s/(function saveReminders\(\))/\Q$native\E\n$1/;

my $tmp = "$file.tmp";
open my $out, ">", $tmp or die "Erro ao criar arquivo temporário: $!";
print $out $html;
close $out or die "Erro ao gravar arquivo temporário.";

system("cp", $tmp, $file) == 0 or die "Erro ao copiar arquivo corrigido.";
unlink $tmp;

print "CORREÇÃO APLICADA!\n";
print "Backup: $backup\n";
print "HTML original preservado fora dos trechos corrigidos.\n";
