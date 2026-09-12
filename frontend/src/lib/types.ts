/** Shapes returned by the Spring Boot API. Kept in one place so a backend
 *  change breaks compilation here instead of failing silently at runtime. */

export type Role = "USER" | "ADMIN";

export type OrderStatus = "PENDING" | "PAID" | "SHIPPED" | "CANCELLED";

export interface Product {
  id: string;
  name: string;
  price: number;
  stock: number;
  description: string;
}

export interface ProductInput {
  name: string;
  price: number;
  stock: number;
  description: string;
}

/** Spring Data serialises pages as { content, page: {...} }. */
export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface OrderItem {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  id: string;
  status: OrderStatus;
  totalPrice: number;
  createdAt: string;
  items: OrderItem[];
}

export interface UserResponse {
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  createdAt: string;
}

/** The single error body produced by GlobalExceptionHandler. */
export interface ApiErrorBody {
  status: number;
  message: string;
  error: string | null;
  timestamp: string;
  /** Only present for @Valid failures: field name -> message. */
  fields: Record<string, string> | null;
}
