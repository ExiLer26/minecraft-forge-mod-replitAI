# Virtual Chest Mod

Minecraft Forge modu - Komut ile erişilebilen sanal sandıklar sağlar.

## Özellikler

- Oyunculara kişisel sanal sandıklar
- Komut tabanlı erişim
- Ayarlanabilir sandık boyutları
- Kalıcı depolama (server yeniden başlatmalarında veriler korunur)
- Admin özellikleri (diğer oyuncuların sandıklarını görüntüleme)

## Uyumluluk

**Bu mod Minecraft 1.12.x versiyonlarını destekler.**

Desteklenen versiyonlar:
- Minecraft 1.12
- Minecraft 1.12.1
- Minecraft 1.12.2

## Kurulum

1. `build/libs/virtualchest-1.0.0.jar` dosyasını indirin
2. Minecraft'ın `mods` klasörüne yerleştirin
3. Minecraft Forge ile oyunu başlatın

## Komutlar

### Temel Kullanım
- `/pv` - 1 numaralı sanal sandığınızı açar
- `/pv <numara>` - Belirtilen numaralı sandığınızı açar (örn: `/pv 2`)

### Admin Komutları
- `/pv reload` - Mod yapılandırmasını yeniden yükler (OP yetkisi gerekir)
- `/pv <oyuncu> <numara>` - Başka bir oyuncunun sandığını görüntüler (OP yetkisi gerekir)

## Yapılandırma

Mod, `config/virtualchest.conf` dosyasından yapılandırılabilir:

- `maxChests`: Oyuncu başına maksimum sandık sayısı
- `chest1Slots` - `chest10Slots`: Her sandık numarası için slot sayısı

## Geliştirme

### Build Etme

```bash
./gradlew clean build
```

Build edilen jar dosyası `build/libs/` klasöründe oluşturulur.

### Versiyon Desteği

Bu mod, `acceptedMinecraftVersions = "[1.12,1.13)"` ayarı ile Minecraft 1.12.x versiyonlarında çalışacak şekilde yapılandırılmıştır.

## Lisans

Bu mod açık kaynak bir projedir.
