#!/usr/bin/env python3
"""Genera kubejs/server_scripts/guia_millenaire.js: libro escrito (vanilla) con la guía
de Millénaire que se entrega al entrar por primera vez y con el comando /guia."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), 'out', 'kubejs', 'server_scripts')
WEB = 'https://github.com/pkatheassh0l3/mine-launcher/blob/main/GUIA-MILLENAIRE.md'

G, D, A, R = 'dark_green', 'dark_gray', 'dark_aqua', 'dark_red'

def t(text, color=None, bold=False):
    c = {"text": text}
    if color: c["color"] = color
    if bold: c["bold"] = True
    return c

def page(title, *body):
    return ["", t(title + "\n\n", G, True)] + [t(b) if isinstance(b, str) else b for b in body]

PAGES = [
    ["", t("Guía de\nMillénaire\n", G, True), t("TFC Create\n\n", D),
     t("Millénaire añade aldeas vivas de 7 culturas: crecen solas, comercian contigo y te dan misiones.\n\n"),
     t("Si pierdes el libro escribe "), t("/guia", A, True)],
    page("Teclas",
         t("V", A, True), " lista las aldeas cercanas.\n\n",
         t("M", A, True), " panel de información (tiene una Ayuda completa).\n\n",
         t("G", A, True), " postura de tus escoltas.\n\n",
         t("Se cambian en Opciones > Controles.", D)),
    page("Encontrar aldeas",
         "Explora el mundo: aparecen en llanuras, colinas, bosques, desiertos, taiga y tundra de TFC.\n\n"
         "Cuando entras en una por primera vez sale un aviso. Pulsa ", t("V", A, True), " para ver dónde están."),
    page("Las 7 culturas",
         "Normandos, hindúes, mayas, japoneses, bizantinos, selyúcidas e inuit.\n\n"
         "Cada una tiene sus edificios, su comida y sus objetos. Los inuit viven en zonas frías y los selyúcidas en desiertos."),
    page("El dinero",
         "Se paga en ", t("denarios", A), ".\n\n"
         "64 denarios = 1 de plata\n64 de plata = 1 de oro\n\n"
         "La ", t("Bolsa de denarios", A), " los guarda juntos. Consigues dinero vendiendo a las aldeas."),
    page("Comerciar",
         "Haz clic derecho en los aldeanos de tiendas y mercados.\n\n"
         "Clic izq = 1, der = 8, Mayús = 64.\n\n"
         "El modo ", t("Donación", A), " regala objetos y da 4 veces más reputación."),
    page("Con TFC",
         "Aquí las aldeas usan objetos de TerraFirmaCraft y Farmer's Delight: trigo, harina, pan, carne, lingotes de TFC...\n\n"
         "Véndeles tus cosechas y metales: es la forma más fácil de ganar dinero y reputación."),
    page("Reputación",
         "Sube al comerciar, donar, completar misiones y encargar edificios.\n\n"
         "Baja si atacas aldeanos o rompes o robas cosas de la aldea.\n\n",
         t("Con reputación negativa no te venden nada.", R)),
    page("Niveles",
         "0 Desconocido\n256 Familiaridad\n1024 Visitante habitual\n4096 Mercader favorito\n8192 Amigo de la aldea\n"
         "32768 Uno de los nuestros\n131072 Líder natural\n\n",
         t("Mira tu nivel con M.", D)),
    page("El jefe",
         "Está en el edificio principal. Clic derecho en él para:\n\n"
         "- ", t("Encargar edificios", A), ": pagas y la aldea crece.\n"
         "- Comprar un ", t("pergamino", A), " de la aldea.\n"
         "- ", t("Diplomacia", A), ": elogiar o difamar a aldeas vecinas (5 veces al día)."),
    page("Misiones",
         "Algunos aldeanos te ofrecen misiones: pulsa ", t("Aceptar", A), " o ", t("Rechazar", A), ".\n\n"
         "El progreso se ve en el panel ", t("M", A, True), ". Dan dinero, objetos y mucha reputación."),
    page("Contratar",
         "Con reputación suficiente puedes contratar soldados y aldeanos por días (clic derecho en ellos).\n\n"
         "Te siguen y te defienden. ", t("G", A, True), " cambia entre postura pasiva y agresiva."),
    page("Tu propia aldea",
         "Con mucha reputación puedes comprar la ", t("Varita de invocación", A), ".\n\n"
         "Úsala en obsidiana para una aldea al azar, o en un bloque de oro para elegir el tipo.\n\n",
         t("La Varita de negación destruye aldeas. ¡Cuidado!", R)),
    page("Consejos",
         "- No rompas bloques de la aldea.\n"
         "- Defiéndela de noche: te lo agradecen.\n"
         "- Planta cultivos de TFC para venderlos.\n"
         "- Si una aldea te odia, prueba con otra cultura."),
    ["", t("Más información\n\n", G, True),
     t("Guía completa en la web:\n\n"),
     {"text": "[Abrir la guía]", "color": "blue", "underlined": True,
      "clickEvent": {"action": "open_url", "value": WEB}},
     t("\n\nY la Ayuda del panel "), t("M", A, True), t(" dentro del juego.\n\n¡Buen viaje!", D)],
]


def snbt_str(s):
    return "'" + s.replace('\\', '\\\\').replace("'", "\\'") + "'"


def item_arg():
    pages = ','.join(snbt_str(json.dumps(p, ensure_ascii=False, separators=(',', ':'))) for p in PAGES)
    return ('minecraft:written_book[minecraft:written_book_content={title:"Guía de Millénaire",'
            'author:"TFC Create",pages:[' + pages + ']}]')


JS = """// Generado por unificacion/guia_gen.py — no editar a mano.
// Da la guía de Millénaire al entrar por primera vez y con el comando /guia.
const GUIA_ITEM = %s

function darGuia(player) {
  player.server.runCommandSilent('give ' + player.username + ' ' + GUIA_ITEM)
}

PlayerEvents.loggedIn(event => {
  const p = event.player
  if (!p.persistentData.getBoolean('guia_millenaire')) {
    p.persistentData.putBoolean('guia_millenaire', true)
    darGuia(p)
    p.tell(Text.green('Te hemos dado la Guía de Millénaire. Si la pierdes, escribe /guia'))
  }
})

ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event
  event.register(Commands.literal('guia').executes(ctx => {
    darGuia(ctx.source.playerOrException)
    return 1
  }))
})
"""

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, 'guia_millenaire.js'), 'w', encoding='utf-8') as f:
        f.write(JS % json.dumps(item_arg(), ensure_ascii=False))
    lens = [len(''.join(c['text'] if isinstance(c, dict) else c for c in p)) for p in PAGES]
    print('páginas:', len(PAGES), 'máx caracteres:', max(lens))
