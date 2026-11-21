package dev.linqfy.bigCasares.modules.casino.loans;

import dev.linqfy.bigCasares.modules.casino.Attraction;
import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.CasinoFormat;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoMenu;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Don Casares at his desk in the back office: instant cash at interest rates that should be illegal. */
public final class LoanShark extends Attraction {

    private final LoanService loans;
    private Mannequin shark;

    public LoanShark(CasinoContext context, Installation installation, Frame frame, LoanService loans) {
        super(context, installation, frame);
        this.loans = loans;
    }

    @Override
    public void spawn() {
        DisplayKit kit = context.kit();
        kit.block(props, frame.at(0, 0, 0.9), Material.DARK_OAK_PLANKS.createBlockData(), 2.2f, 0.9f, 0.9f);
        kit.block(props, frame.at(0, 0.9, 0.9), Material.BLACK_CONCRETE.createBlockData(), 2.3f, 0.05f, 1.0f);
        kit.block(props, frame.at(0.7, 0.95, 0.8), Material.LANTERN.createBlockData(), 0.35f, 0.4f, 0.35f);
        kit.item(props, frame.at(-0.4, 0.96, 0.9), new ItemStack(Material.PAPER), 0.35f, DisplayKit.FLAT);
        kit.item(props, frame.at(-0.2, 0.97, 1.0), new ItemStack(Material.WRITABLE_BOOK), 0.3f, DisplayKit.FLAT);
        kit.item(props, frame.at(0.1, 0.96, 0.75), CasinoItems.chip(5000), 0.25f, DisplayKit.FLAT);
        kit.block(props, frame.at(0, 0, -0.1), Material.DARK_OAK_STAIRS.createBlockData("[facing=south]"), 0.9f, 0.9f, 0.9f);
        ArmorStand chair = kit.seat(props, frame.at(0, 0.5, -0.05, 0));
        shark = kit.npc(props, frame.at(0, 0.6, -0.05, 0), "§5§lDon Casares", "§7Préstamos sin preguntas", "");
        chair.addPassenger(shark);
        context.router().bind(shark, this::open);
        Interaction desk = kit.interaction(props, frame.at(0, 0, 0.9), 2.2f, 1.1f);
        context.router().bind(desk, this::open);
        kit.text(props, frame.at(0, 2.6, 0), CasinoText.legacy("§5§lLA CUEVA DE LA USURA\n§7\"Pagás o pagás\""),
            0.8f, true, 0x99000000);
    }

    @Override
    protected void onTick(long tick) {
        if (tick % 80 == 0 && shark != null && shark.isValid()) {
            shark.getWorld().spawnParticle(Particle.SMOKE, shark.getEyeLocation().add(frame.forwardVector().multiply(0.3)),
                3, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private void open(Player player) {
        CasinoMenu menu = new CasinoMenu(3, "§5Don Casares");
        Optional<Loan> current = loans.loan(player.getUniqueId());
        if (current.isPresent()) {
            long balance = loans.balance(player.getUniqueId());
            long left = current.get().dueAtMillis() - context.now();
            menu.set(4, CasinoItems.button(Material.WRITABLE_BOOK, "§5Tu deuda: §c" + context.chips(balance),
                left > 0 ? "§7Vence en §f" + CasinoFormat.duration(left) : "§4§lVENCIDA §7· la AFIP ya te busca",
                "§7Préstamo original: §f" + context.chips(current.get().principal())), null);
            menu.set(11, CasinoItems.button(Material.EMERALD, "§aPagar todo", "§7" + context.chips(balance)),
                (clicker, click) -> pay(clicker, Long.MAX_VALUE));
            menu.set(13, CasinoItems.button(Material.GOLD_NUGGET, "§ePagar un 25%",
                "§7" + context.chips(Math.max(1, balance / 4))), (clicker, click) -> pay(clicker, Math.max(1, balance / 4)));
            menu.set(15, CasinoItems.button(Material.SKELETON_SKULL, "§7Pedir más",
                "§8Don Casares te mira fijo.", "§8Primero pagá lo que debés."), (clicker, click) ->
                clicker.sendMessage("§5[Don Casares] §7¿Más? Primero devolvé lo mío, pibe."));
        } else {
            int slot = 11;
            double rating = loans.creditRating(player.getUniqueId());
            String history = rating < 1 ? "§aTasa preferencial: tu moneda tiene prestigio bancario."
                : rating > 1 ? "§cDon Casares recuerda tus rug pulls: tasa de castigo." : "§8Historial crediticio limpio.";
            for (LoanOffer base : loans.settings().offers()) {
                if (slot > 15) {
                    break;
                }
                LoanOffer offer = loans.offerFor(player.getUniqueId(), base);
                menu.set(slot, CasinoItems.button(Material.GOLD_INGOT, "§6Préstamo de " + context.chips(offer.principal()),
                    "§7Devolvés: §c" + context.chips(offer.repayment()) + " §8(+" + Math.round(offer.interest() * 100) + "%)",
                    "§7Plazo: §f" + CasinoFormat.duration(offer.termMinutes() * 60_000L),
                    "§7Mora: §c+" + Math.round(loans.settings().lateFeePerHour() * 100) + "% por hora",
                    history,
                    "§4Si no pagás, viene el Cobrador de la AFIP."), (clicker, click) -> borrow(clicker, offer));
                slot += 2;
            }
        }
        menu.fill(CasinoItems.filler());
        menu.open(player);
        context.sound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.6f, 0.5f);
    }

    private void borrow(Player player, LoanOffer offer) {
        try {
            loans.take(player.getUniqueId(), offer);
        } catch (IllegalStateException refused) {
            player.sendMessage("§5[Don Casares] §c" + refused.getMessage());
            return;
        }
        player.closeInventory();
        player.sendMessage("§5[Don Casares] §fAhí tenés " + context.chips(offer.principal()) + ". Me devolvés "
            + context.chips(offer.repayment()) + " en " + CasinoFormat.duration(offer.termMinutes() * 60_000L)
            + ". §7Y no me hagas mandar a nadie.");
        context.sound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.7f);
    }

    private void pay(Player player, long amount) {
        long paid = loans.repayFromBalance(player.getUniqueId(), amount);
        player.closeInventory();
        if (paid <= 0) {
            player.sendMessage("§5[Don Casares] §cNo tenés ni para el café.");
            return;
        }
        if (loans.loan(player.getUniqueId()).isEmpty()) {
            player.sendMessage("§5[Don Casares] §aEstamos a mano. §7Un placer hacer negocios.");
        } else {
            player.sendMessage("§5[Don Casares] §7Recibí " + context.chips(paid) + ". Te faltan §c"
                + context.chips(loans.balance(player.getUniqueId())) + "§7.");
        }
    }
}
