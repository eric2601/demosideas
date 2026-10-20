export type AuthStackParams = {
  Login: undefined;
};

export type TabParams = {
  Inicio: undefined;
  Catalogo: undefined;
  Pedidos: { filter?: import('../lib/status').FilterKey } | undefined;
  Avisos: undefined;
};

export type AppStackParams = {
  Tabs: undefined;
  Resumen: undefined;
  DetallePedido: { orderId: number; justCreated?: boolean };
  CambiarClave: undefined;
};
